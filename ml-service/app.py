import hmac
import io
import base64
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from pydantic import BaseModel, ConfigDict, Field
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GroupShuffleSplit, train_test_split

FEATURES = ["attendance_percent", "average_marks_percent"]
TARGET = "final_score"
MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MAX_MODEL_BYTES = 25 * 1024 * 1024
MODEL_PATH = Path(os.getenv("MODEL_PATH", "artifacts/eduinsight-model.joblib"))
API_KEY = os.getenv("ML_API_KEY", "")
app = FastAPI(title="EduInsight ML Service", version="1.0.0")


class StudentFeatures(BaseModel):
    attendance_percent: float = Field(ge=0, le=100)
    average_marks_percent: float = Field(ge=0, le=100)


class PredictionRequest(BaseModel):
    samples: list[StudentFeatures] = Field(min_length=1, max_length=500)
    model_artifact: str | None = Field(default=None, alias="modelArtifact")
    model_config = ConfigDict(populate_by_name=True)


def require_api_key(value: str) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="ML_API_KEY is not configured on the prediction service.")
    if not hmac.compare_digest(value.encode(), API_KEY.encode()):
        raise HTTPException(status_code=401, detail="Invalid service credential.")


def load_artifact() -> dict | None:
    if not MODEL_PATH.is_file():
        return None
    return joblib.load(MODEL_PATH)


@app.get("/health")
def health():
    artifact = load_artifact()
    return {
        "status": "ok",
        "modelTrained": artifact is not None,
        "modelMetrics": artifact.get("metrics") if artifact else None,
        "trainedAt": artifact.get("trained_at") if artifact else None,
    }


@app.post("/train")
async def train(file: Annotated[UploadFile, File()], x_api_key: Annotated[str, Header()] = ""):
    require_api_key(x_api_key)
    raw = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="CSV must be 10 MB or smaller.")
    if not raw:
        raise HTTPException(status_code=400, detail="CSV file is empty.")
    try:
        data = pd.read_csv(io.BytesIO(raw))
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Could not read the uploaded CSV.") from exc
    missing = [column for column in FEATURES + [TARGET] if column not in data.columns]
    if missing:
        raise HTTPException(status_code=400, detail={
            "message": "CSV is missing required columns.",
            "requiredColumns": FEATURES + [TARGET],
            "missingColumns": missing,
        })
    if len(data) > 10_000:
        raise HTTPException(status_code=400, detail="CSV must contain 10,000 rows or fewer.")
    if len(data) < 30:
        raise HTTPException(status_code=400, detail="At least 30 labeled rows are required for a train/test split.")
    data_rows = len(data)
    numeric_data = data[FEATURES + [TARGET]].apply(pd.to_numeric, errors="coerce")
    if numeric_data.isna().any().any():
        raise HTTPException(status_code=400, detail="All feature and final_score values must be present and numeric; fix invalid rows and retry.")
    if "student_group" in data.columns:
        groups = data["student_group"].astype("string").str.strip()
        if groups.isna().any() or groups.eq("").any():
            raise HTTPException(status_code=400, detail="student_group must be filled on every row when supplied.")
        if groups.nunique() < 8:
            raise HTTPException(status_code=400, detail="At least eight distinct student_group values are required for a grouped holdout split.")
    else:
        groups = None
    data = numeric_data
    values = data.to_numpy(dtype=float)
    if not np.isfinite(values).all():
        raise HTTPException(status_code=400, detail="Dataset contains non-finite numbers.")
    if ((data[FEATURES + [TARGET]] < 0) | (data[FEATURES + [TARGET]] > 100)).any().any():
        raise HTTPException(status_code=400, detail="Feature and final_score values must be between 0 and 100.")

    X = data[FEATURES]
    y = data[TARGET]
    if groups is not None:
        train_indices, test_indices = next(GroupShuffleSplit(
            n_splits=1, test_size=0.25, random_state=42
        ).split(X, y, groups=groups))
        if len(train_indices) < 15 or len(test_indices) < 8:
            raise HTTPException(status_code=400, detail="The grouped split must produce at least 15 training rows and 8 holdout rows; check student_group distribution.")
        X_train, X_test = X.iloc[train_indices], X.iloc[test_indices]
        y_train, y_test = y.iloc[train_indices], y.iloc[test_indices]
    else:
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.25, random_state=42
        )
    model = RandomForestRegressor(
        n_estimators=200, min_samples_leaf=5, max_depth=10, random_state=42, n_jobs=-1
    )
    model.fit(X_train, y_train)
    predicted = np.clip(model.predict(X_test), 0, 100)
    metrics = {
        "mae": round(float(mean_absolute_error(y_test, predicted)), 3),
        "rmse": round(float(np.sqrt(mean_squared_error(y_test, predicted))), 3),
        "r2": round(float(r2_score(y_test, predicted)), 4),
        "datasetRows": int(data_rows),
        "trainRows": int(len(X_train)),
        "testRows": int(len(X_test)),
    }
    trained_at = datetime.now(timezone.utc).isoformat()
    artifact = {"model": model, "features": FEATURES, "metrics": metrics, "trained_at": trained_at}
    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=MODEL_PATH.parent, delete=False) as temp:
        temp_path = Path(temp.name)
    try:
        joblib.dump(artifact, temp_path)
        artifact_bytes = temp_path.read_bytes()
        if len(artifact_bytes) > MAX_MODEL_BYTES:
            raise HTTPException(status_code=400, detail="Trained model exceeds the 25 MB storage limit; upload a smaller cohort dataset.")
        temp_path.replace(MODEL_PATH)
    finally:
        temp_path.unlink(missing_ok=True)
    return {"modelTrained": True, "algorithm": "RandomForestRegressor", "features": FEATURES,
            "metrics": metrics, "trainedAt": trained_at,
            "modelArtifact": base64.b64encode(artifact_bytes).decode("ascii")}


@app.post("/predict")
def predict(request: PredictionRequest, x_api_key: Annotated[str, Header()] = ""):
    require_api_key(x_api_key)
    if request.model_artifact:
        try:
            artifact_bytes = base64.b64decode(request.model_artifact, validate=True)
            if len(artifact_bytes) > MAX_MODEL_BYTES:
                raise ValueError("Artifact is too large.")
            artifact = joblib.load(io.BytesIO(artifact_bytes))
        except Exception as exc:
            raise HTTPException(status_code=400, detail="Stored model artifact is invalid.") from exc
    else:
        artifact = load_artifact()
    if artifact is None:
        raise HTTPException(status_code=503, detail="No trained model is available. Upload a labeled CSV to /train first.")
    frame = pd.DataFrame([sample.model_dump() for sample in request.samples], columns=FEATURES)
    predictions = np.clip(artifact["model"].predict(frame), 0, 100)
    return {
        "predictions": [round(float(value), 2) for value in predictions],
        "modelMetrics": artifact["metrics"],
        "trainedAt": artifact["trained_at"],
        "target": "final_score",
    }
