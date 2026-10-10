# EduInsight ML service

This service trains a regression model only from a real, labeled CSV supplied by an administrator. It never seeds sample data or reports placeholder metrics.

Required CSV columns (all numeric, values from 0 to 100):

- `attendance_percent`
- `average_marks_percent`
- `final_score` — the observed future/term outcome the model is being trained to estimate

Use one historical student-term per row. Do not include student names, email addresses, or other direct identifiers. At least 30 complete rows are required. For students who appear in multiple terms, add a stable pseudonymous `student_group` column; the service then keeps each student wholly in either training or holdout data. Without it, the service assumes rows are independent. The service holds out 25% using a fixed random seed, trains a `RandomForestRegressor`, and reports MAE, RMSE, R², and split sizes on that holdout set. Those metrics describe the uploaded cohort and should not be treated as proof of generalization to another institution or term. Invalid or missing feature rows are rejected so the system does not silently exclude them.

Set `ML_API_KEY` to a long random secret. `/train` and `/predict` require it in the `X-API-Key` header. `/health` reports `modelTrained: false` until a model file exists. The Spring Boot service saves the returned trained artifact in the Aiven database and sends it with prediction requests, so the active model survives Python service restarts. The local artifact file is only a development fallback.

Run locally:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:ML_API_KEY = "use-a-long-random-local-secret"
uvicorn app:app --reload --port 8000
```
