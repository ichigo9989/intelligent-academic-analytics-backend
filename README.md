# Intelligent Academic Analytics and Student Performance Prediction

EduInsight is a React, Spring Boot, MySQL, and Python application for student profiles, attendance, assessment marks, transparent risk flags, CSV reports, and supervised performance estimates.

The backend Git repository contains the Spring Boot API and the `ml-service` used for training and predictions. The frontend is maintained in the separate `intelligent-academic-analytics-frontend` repository.

## Live project

- **Application:** [https://ichigoo789.netlify.app](https://ichigoo789.netlify.app)
- **Spring Boot API:** [https://academic-analytics-backend.onrender.com](https://academic-analytics-backend.onrender.com)
- **ML service:** [https://academic-analytics-ml.onrender.com](https://academic-analytics-ml.onrender.com)
- **Frontend repository:** [https://github.com/ichigo9989/intelligent-academic-analytics-frontend](https://github.com/ichigo9989/intelligent-academic-analytics-frontend)

## Features

- JWT sign-in for administrator, faculty, and student roles; passwords are BCrypt-hashed.
- Public student registration; administrator-created faculty accounts.
- Faculty/admin CRUD for student profiles, attendance, and marks.
- Student endpoints return only that account's profile, attendance, and marks.
- Database-backed averages and a visible at-risk policy: attendance below 75% or marks below 40%; high risk is below 60% attendance or below 30% marks.
- CSV exports for roster, attendance, marks, and at-risk reviews.
- Random forest regression trained from a supplied labeled CSV. Holdout MAE, RMSE, R², and split sizes are stored with the model artifact in MySQL. No dataset, model, or accuracy result is seeded.

## Local development

Requirements: Java 21, MySQL 8, Python 3.12, and Node.js 22.

1. Create a local MySQL database named `academic_analytics` and configure the ignored `src/main/resources/application-local.properties` with local-only credentials. Never commit that file or production secrets.
2. Start the ML service in a terminal:

   ```powershell
   cd ml-service
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   python -m pip install -r requirements.txt
   $env:ML_API_KEY = "set-a-random-local-secret"
   python -m uvicorn app:app --reload --port 8000
   ```

3. Start the backend from this directory in another terminal. Set the same local `ML_API_KEY` value used by the ML service:

   ```powershell
   $env:JWT_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   $env:ML_API_KEY = "the-same-random-local-secret"
   $env:ML_SERVICE_URL = "http://localhost:8000"
   .\mvnw.cmd spring-boot:run
   ```

   The API listens on `http://localhost:8081`. If creating the initial administrator locally, set both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` before startup. The password must be 12–72 characters and at most 72 UTF-8 bytes. No default account is created. The model artifact is saved by the API in MySQL, so it survives ML service restarts.

4. In the frontend repository:

   ```powershell
   npm ci
   $env:VITE_API_BASE_URL = "http://localhost:8081"
   npm run dev
   ```

5. Verify with `./mvnw.cmd -B test` in this directory, and `npm run lint` plus `npm run build` in the frontend repository.

## Render and Netlify deployment

The Render Blueprint in `render.yaml` creates the Spring Boot API and Python ML service. Use **New Blueprint Instance** with this GitHub repository. Enter these values in Render's secret/environment prompts; do not put credentials in Git:

| Service | Environment variable | Value |
| --- | --- | --- |
| Backend | `DB_HOST` | Aiven host name |
| Backend | `DB_PORT` | Aiven MySQL port |
| Backend | `DB_USER` / `DB_PASSWORD` | Aiven database credentials |
| Backend | `FRONTEND_ORIGIN` | Exact Netlify site origin; use `http://localhost:5173` temporarily if Netlify is not deployed yet, then change it and redeploy |
| Backend | `JWT_SECRET` | Random value with at least 32 bytes |
| Backend | `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | Initial administrator login; both required for first bootstrap |
| Backend and ML service | `ML_API_KEY` | The same long random secret in both services |

`DB_NAME` is set to `academic_analytics`; the production JDBC URL requires TLS. Type the database password and administrator password into Render's secret fields only. The first backend startup creates the administrator if that email does not already exist.

After Render creates both services, copy the ML service's public URL into the backend's `ML_SERVICE_URL` environment variable and redeploy it. For Netlify, import the frontend repository. `netlify.toml` sets the Vite build command and `dist` publish directory. Set `VITE_API_BASE_URL` to the Render backend root URL, with no `/api` suffix. Keep `FRONTEND_ORIGIN` equal to the deployed Netlify origin, then redeploy the backend after changing it.

Check the backend health at `https://<backend-host>/api/test`. After deployment, sign in as the bootstrap administrator, create faculty accounts under Settings, and register student accounts from the login page.

## ML dataset and behavior

The administrator uploads a CSV in **AI Predictions**. Required numeric columns are:

```text
attendance_percent,average_marks_percent,final_score
```

Values must be 0–100 and at least 30 complete rows are required. Use historical records where attendance and marks are measured before the `final_score` outcome. If a student appears in multiple terms, add a stable pseudonymous `student_group` column so the holdout split keeps each student in only one partition. Do not upload names, emails, or other direct identifiers. The model target and holdout metrics are shown with each prediction. If there is no valid trained model, the prediction endpoint returns unavailable instead of an invented score.

The CSV header template is [ml-service/dataset-template.csv](ml-service/dataset-template.csv); detailed pipeline notes are in [ml-service/README.md](ml-service/README.md).

## Main API routes

| Route | Access | Purpose |
| --- | --- | --- |
| `POST /api/auth/login` | Public | Login |
| `POST /api/auth/register` | Public | Register a student account |
| `POST /api/auth/faculty` | Admin | Create faculty login |
| `/api/students` | Admin/faculty | Student profile CRUD |
| `/api/attendance` | Admin/faculty | Attendance CRUD |
| `/api/marks` | Admin/faculty | Marks CRUD |
| `GET /api/analytics/summary` | Admin/faculty | Averages and explained risk queue |
| `/api/predictions` | Admin/faculty; model training is admin-only | Model status, training, and estimates |
| `/api/auth/me/attendance`, `/api/auth/me/marks` | Student | Own academic records |
