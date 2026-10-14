import base64
import hmac
import io
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated, Any

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from pydantic import BaseModel, ConfigDict, Field
from sklearn.ensemble import GradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GroupKFold, GroupShuffleSplit, KFold, cross_val_score, train_test_split
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MAX_MODEL_BYTES = 25 * 1024 * 1024
MODEL_PATH = Path(os.getenv("MODEL_PATH", "artifacts/eduinsight-model.joblib"))
API_KEY = os.getenv("ML_API_KEY", "")
MODEL_VERSION = "1.2.0"

# Each supported CSV is an independent cohort/profile. Their mark scales differ,
# so they must not be merged into one training set.
DATASET_SCHEMAS = [
    {
        "model_key": "study_hours_marks",
        "label": "Study hours → student marks",
        "features": ["study_hours"],
        "target": "student_marks",
    },
    {
        "model_key": "course_study_marks",
        "label": "Courses and study time → marks",
        "features": ["number_courses", "time_study"],
        "target": "Marks",
    },
    {
        "model_key": "active",
        "label": "Attendance and current marks → final score",
        "features": ["attendance_percent", "average_marks_percent"],
        "target": "final_score",
    },
]

app = FastAPI(title="EduInsight ML Service", version=MODEL_VERSION)


class StudentFeatures(BaseModel):
    attendance_percent: float = Field(ge=0, le=100)
    average_marks_percent: float = Field(ge=0, le=100)


class PredictionRequest(BaseModel):
    model_key: str | None = Field(default=None, alias="modelKey")
    features: dict[str, float] | None = None
    samples: list[StudentFeatures] | None = Field(default=None, min_length=1, max_length=500)
    model_artifact: str | None = Field(default=None, alias="modelArtifact")
    model_config = ConfigDict(populate_by_name=True)


def require_api_key(value: str) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="ML_API_KEY is not configured on the prediction service.")
    if not hmac.compare_digest(value.encode(), API_KEY.encode()):
        raise HTTPException(status_code=401, detail="Invalid service credential.")


def model_path(model_key: str = "active") -> Path:
    if model_key == "active":
        return MODEL_PATH
    return MODEL_PATH.with_name(f"{MODEL_PATH.stem}-{model_key}{MODEL_PATH.suffix}")


def load_artifact(model_key: str = "active") -> dict[str, Any] | None:
    path = model_path(model_key)
    if not path.is_file():
        return None
    return joblib.load(path)


def profile_summary(artifact: dict[str, Any]) -> dict[str, Any]:
    return {
        "modelKey": artifact["model_key"],
        "label": artifact["label"],
        "features": artifact["features"],
        "target": artifact["target"],
        "algorithm": artifact["algorithm"],
        "modelVersion": artifact["model_version"],
        "metrics": artifact["metrics"],
        "crossValidationMae": artifact.get("cross_validation_mae", {}),
        "trainedAt": artifact["trained_at"],
    }


@app.get("/health")
def health():
    artifacts = []
    for schema in DATASET_SCHEMAS:
        artifact = load_artifact(schema["model_key"])
        if artifact is not None:
            artifacts.append(profile_summary(artifact))
    return {
        "status": "ok",
        "modelTrained": bool(artifacts),
        "algorithm": artifacts[0]["algorithm"] if artifacts else None,
        "modelVersion": MODEL_VERSION if artifacts else None,
        "modelMetrics": artifacts[0]["metrics"] if artifacts else None,
        "trainedAt": artifacts[0]["trainedAt"] if artifacts else None,
        "trainedProfiles": artifacts,
    }


def fit_dataset(raw: bytes, filename: str) -> tuple[dict[str, Any], bytes]:
    try:
        data = pd.read_csv(io.BytesIO(raw))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Could not read {filename} as CSV.") from exc
    if len(data) > 10_000:
        raise HTTPException(status_code=400, detail=f"{filename} must contain 10,000 rows or fewer.")

    matches = [schema for schema in DATASET_SCHEMAS
               if set(schema["features"] + [schema["target"]]).issubset(data.columns)]
    if len(matches) != 1:
        required = ["study_hours, student_marks", "number_courses, time_study, Marks",
                    "attendance_percent, average_marks_percent, final_score"]
        raise HTTPException(status_code=400, detail={
            "message": f"{filename} does not match exactly one supported dataset format.",
            "supportedFormats": required,
        })
    schema = matches[0]
    columns = schema["features"] + [schema["target"]]
    numeric = data[columns].apply(pd.to_numeric, errors="coerce")
    valid = numeric.notna().all(axis=1)
    groups = None
    if "student_group" in data.columns:
        raw_groups = data["student_group"].astype("string").str.strip()
        if raw_groups.loc[valid].isna().any() or raw_groups.loc[valid].eq("").any():
            raise HTTPException(status_code=400, detail="student_group must be filled for every usable row when supplied.")
        groups = raw_groups.loc[valid].reset_index(drop=True)
    numeric = numeric.loc[valid].reset_index(drop=True)
    rows_dropped = int(len(data) - len(numeric))
    if len(numeric) < 30:
        raise HTTPException(status_code=400, detail=f"{filename} needs at least 30 complete numeric rows; found {len(numeric)}.")
    values = numeric.to_numpy(dtype=float)
    if not np.isfinite(values).all():
        raise HTTPException(status_code=400, detail=f"{filename} contains infinite or invalid numbers.")
    if (numeric[schema["features"]] < 0).any().any():
        raise HTTPException(status_code=400, detail=f"{filename} contains negative feature values.")
    if ((numeric[schema["target"]] < 0) | (numeric[schema["target"]] > 100)).any():
        raise HTTPException(status_code=400, detail=f"{filename} target marks must be between 0 and 100.")
    if groups is not None and groups.nunique() < 8:
        raise HTTPException(status_code=400, detail=f"{filename} needs at least eight distinct student_group values for a grouped holdout.")

    features = schema["features"]
    target = schema["target"]
    percent_features = [name for name in features if name.endswith("_percent")]
    if percent_features and (numeric[percent_features] > 100).any().any():
        raise HTTPException(status_code=400, detail="Percentage feature values must be between 0 and 100.")
    X = numeric[features]
    y = numeric[target]
    if groups is not None:
        train_indices, test_indices = next(GroupShuffleSplit(
            n_splits=1, test_size=0.25, random_state=42
        ).split(X, y, groups=groups))
        if len(train_indices) < 15 or len(test_indices) < 8:
            raise HTTPException(status_code=400, detail=f"{filename} grouped split is too small for evaluation.")
        X_train, X_test = X.iloc[train_indices], X.iloc[test_indices]
        y_train, y_test = y.iloc[train_indices], y.iloc[test_indices]
        train_groups = groups.iloc[train_indices]
        cv = GroupKFold(n_splits=min(5, int(train_groups.nunique())))
    else:
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.25, random_state=42
        )
        train_groups = None
        cv = KFold(n_splits=5, shuffle=True, random_state=42)

    candidates = {
        "RidgeRegressor": make_pipeline(StandardScaler(), Ridge(alpha=1.0)),
        "RandomForestRegressor": RandomForestRegressor(
            n_estimators=200, min_samples_leaf=5, max_depth=10, random_state=42, n_jobs=-1
        ),
        "GradientBoostingRegressor": GradientBoostingRegressor(
            n_estimators=100, learning_rate=0.05, max_depth=2, loss="huber", random_state=42
        ),
    }
    comparison_mae = {}
    for name, candidate in candidates.items():
        fold_scores = cross_val_score(
            candidate, X_train, y_train, groups=train_groups, cv=cv,
            scoring="neg_mean_absolute_error", n_jobs=1,
        )
        comparison_mae[name] = round(float(-fold_scores.mean()), 3)
    algorithm = min(comparison_mae, key=comparison_mae.get)
    model = candidates[algorithm]
    model.fit(X_train, y_train)
    predicted = model.predict(X_test)
    metrics = {
        "mae": round(float(mean_absolute_error(y_test, predicted)), 3),
        "rmse": round(float(np.sqrt(mean_squared_error(y_test, predicted))), 3),
        "r2": round(float(r2_score(y_test, predicted)), 4),
        "cvMae": comparison_mae[algorithm],
        "datasetRows": int(len(numeric)),
        "rowsDropped": rows_dropped,
        "trainRows": int(len(X_train)),
        "testRows": int(len(X_test)),
        "cvSplits": int(cv.get_n_splits(X_train, y_train, train_groups)),
    }
    trained_at = datetime.now(timezone.utc).isoformat()
    artifact = {
        "model": model,
        "model_key": schema["model_key"],
        "label": schema["label"],
        "features": features,
        "target": target,
        "target_min": float(y.min()),
        "target_max": float(y.max()),
        "feature_ranges": {
            name: [float(X[name].min()), float(X[name].max())] for name in features
        },
        "metrics": metrics,
        "trained_at": trained_at,
        "algorithm": algorithm,
        "model_version": MODEL_VERSION,
        "cross_validation_mae": comparison_mae,
    }
    artifact_buffer = io.BytesIO()
    joblib.dump(artifact, artifact_buffer)
    artifact_bytes = artifact_buffer.getvalue()
    if len(artifact_bytes) > MAX_MODEL_BYTES:
        raise HTTPException(status_code=400, detail=f"The trained model for {filename} exceeds the 25 MB limit.")
    return artifact, artifact_bytes


@app.post("/train")
async def train(files: Annotated[list[UploadFile], File()], x_api_key: Annotated[str, Header()] = ""):
    require_api_key(x_api_key)
    if not files or len(files) > 5:
        raise HTTPException(status_code=400, detail="Upload between 1 and 5 CSV files.")
    trained = []
    seen_keys = set()
    for file in files:
        filename = file.filename or "dataset.csv"
        raw = await file.read(MAX_UPLOAD_BYTES + 1)
        if len(raw) > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail=f"{filename} must be 10 MB or smaller.")
        if not raw:
            raise HTTPException(status_code=400, detail=f"{filename} is empty.")
        artifact, artifact_bytes = fit_dataset(raw, filename)
        if artifact["model_key"] in seen_keys:
            raise HTTPException(status_code=400, detail=f"Only one CSV per model profile is allowed ({artifact['model_key']}).")
        seen_keys.add(artifact["model_key"])
        trained.append((artifact, artifact_bytes))

    # Only persist profiles after every uploaded file has passed validation and training.
    for artifact, artifact_bytes in trained:
        path = model_path(artifact["model_key"])
        path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temp:
            temp_path = Path(temp.name)
        try:
            temp_path.write_bytes(artifact_bytes)
            temp_path.replace(path)
        finally:
            temp_path.unlink(missing_ok=True)

    return {
        "modelTrained": bool(trained),
        "modelVersion": MODEL_VERSION,
        "models": [
            {**profile_summary(artifact), "modelArtifact": base64.b64encode(artifact_bytes).decode("ascii")}
            for artifact, artifact_bytes in trained
        ],
    }


@app.post("/predict")
def predict(request: PredictionRequest, x_api_key: Annotated[str, Header()] = ""):
    require_api_key(x_api_key)
    try:
        if request.model_artifact:
            artifact_bytes = base64.b64decode(request.model_artifact, validate=True)
            if len(artifact_bytes) > MAX_MODEL_BYTES:
                raise ValueError("Artifact is too large.")
            artifact = joblib.load(io.BytesIO(artifact_bytes))
        else:
            artifact = load_artifact(request.model_key or "active")
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Stored model artifact is invalid.") from exc
    if artifact is None:
        raise HTTPException(status_code=503, detail="No trained model is available for this profile.")

    if request.features is not None:
        expected = artifact["features"]
        missing = [name for name in expected if name not in request.features]
        if missing:
            raise HTTPException(status_code=400, detail={"message": "Prediction inputs are incomplete.", "missingFeatures": missing})
        row = {name: request.features[name] for name in expected}
        if not all(np.isfinite(value) and value >= 0 for value in row.values()):
            raise HTTPException(status_code=400, detail="Prediction inputs must be finite non-negative numbers.")
        ranges = artifact.get("feature_ranges", {})
        for name, value in row.items():
            limits = ranges.get(name)
            if limits and (value < limits[0] or value > limits[1]):
                raise HTTPException(status_code=400, detail=(
                    f"{name} must be within the trained data range ({limits[0]:.3f} to {limits[1]:.3f})."
                ))
            if name.endswith("_percent") and value > 100:
                raise HTTPException(status_code=400, detail=f"{name} must be between 0 and 100.")
        frame = pd.DataFrame([row], columns=expected)
    elif request.samples:
        frame = pd.DataFrame([sample.model_dump() for sample in request.samples], columns=artifact["features"])
    else:
        raise HTTPException(status_code=400, detail="Provide either a feature map or student samples to predict.")

    predictions = artifact["model"].predict(frame)
    predictions = np.clip(predictions, artifact["target_min"], artifact["target_max"])
    return {
        "predictions": [round(float(value), 2) for value in predictions],
        "modelMetrics": artifact["metrics"],
        "algorithm": artifact["algorithm"],
        "modelVersion": artifact["model_version"],
        "trainedAt": artifact["trained_at"],
        "target": artifact["target"],
        "modelKey": artifact["model_key"],
        "features": artifact["features"],
    }
