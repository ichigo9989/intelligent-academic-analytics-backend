# EduInsight ML service

This service trains a regression model only from a real, labeled CSV supplied by an administrator. It never seeds sample data or reports placeholder metrics.

Upload one or more independent CSVs. Each matching schema is trained and stored as its own model profile; files with different feature columns or target scales are never merged. Supported numeric schemas:

- `study_hours` → `student_marks` (`study_hours_marks` profile)
- `number_courses`, `time_study` → `Marks` (`course_study_marks` profile)
- `attendance_percent`, `average_marks_percent` → `final_score` (`active` profile)

Use one suitable observation per row. Do not include student names, email addresses, or other direct identifiers. Each file needs at least 30 complete rows; rows with missing or non-numeric model columns are dropped and counted. Target marks must be between 0 and 100; percentage features must also be between 0 and 100. For repeated student terms, add a stable pseudonymous `student_group` column; the service keeps each student wholly in either training or holdout data and cross-validation folds. Without it, the service assumes rows are independent. The service holds out 25% using a fixed random seed, compares a scaled `RidgeRegressor`, `RandomForestRegressor`, and `GradientBoostingRegressor` through five-fold cross-validation on the training partition, then selects the lowest-CV-MAE candidate. It reports MAE, RMSE, R², and split sizes on the untouched holdout set. Prediction inputs outside the feature range observed during training are rejected. Metrics describe the uploaded cohort and are not proof of generalization to another institution or term.

Set `ML_API_KEY` to a long random secret. `/train` and `/predict` require it in the `X-API-Key` header. `/health` reports each local trained profile. The Spring Boot service saves every returned artifact in the Aiven database and sends the selected artifact with prediction requests, so deployed predictions survive Python service restarts. The local artifact files are a development fallback.

Run locally:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:ML_API_KEY = "use-a-long-random-local-secret"
uvicorn app:app --reload --port 8000
```
