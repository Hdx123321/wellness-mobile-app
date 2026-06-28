# WellnessMate AI

WellnessMate AI is an integrated Kotlin Android and Java Spring Boot application for recording wellness data and receiving non-diagnostic AI wellness guidance.

## Current status

Stage 1 is complete:

- Spring Boot modular-monolith skeleton with Actuator, JPA, Flyway, and MySQL configuration.
- Initial schema for users, wellness records, chat history, and recommendations.
- Jetpack Compose Android skeleton that calls the backend health endpoint.
- Docker Compose development environment.

Backend slice B1 is also complete:

- BCrypt client registration and username/email login.
- HS256 JWT access tokens and stateless API protection.
- First-login onboarding question catalog and private user profile.
- Login responses expose `onboardingRequired` so Android can enforce the onboarding flow.

Backend slice B2 built-in trackers are complete:

- Food, weight, workout, steps, sleep, and water type metadata.
- JWT-owned tracker entry create, read, update, delete, pagination, type filtering, and date filtering.
- Type-specific units and validation.

Android core flow is implemented:

- Registration/login, encrypted JWT session restore, first-login onboarding, and logout.
- Dashboard for the six built-in trackers.
- Tracker history, filtering, shared create/edit form, delete confirmation, loading, empty, and error states.
- Navigation Compose with debug API base URL `http://10.0.2.2:18080/`.

The Android core flow has been interaction-tested on a Pixel 10 Pro API 36.1 AVD: registration, onboarding, tracker create/edit/delete, encrypted session restore, logout, and login all passed.

The project builds successfully, Flyway migrations run against MySQL 8.4, and the containerized health endpoint reports `UP`. Device capabilities, coach chat, and AI recommendations remain later slices.

## Repository layout

```text
android-app/     Kotlin + Jetpack Compose client
backend/         Java 21 + Spring Boot backend
docs/            Architecture, API, and authorship records
infra/           Infrastructure support files
```

## Prerequisites

- Java 21 or newer (the build targets Java 21)
- Docker Desktop
- Android SDK API 36.1

The Android minimum SDK is provisionally set to 26 and must be confirmed by the team. Emulator traffic uses `http://10.0.2.2:18080` only in debug builds; release builds continue to require HTTPS.

## Run the backend stack

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Backend health: `http://localhost:18080/actuator/health`

The project defaults to host port 18080 because port 8080 is occupied in the current development environment. Override `BACKEND_PORT` and Android `-PAPI_BASE_URL` together when using another port.

## Verify locally

```powershell
cd backend
.\mvnw.cmd test

cd ..\android-app
.\gradlew.bat testDebugUnitTest assembleDebug
```

Do not commit `.env`, API keys, JWT secrets, APK files, build directories, or database volumes.

## Configuration still required

- Team name, member names, feature ownership, and final authorship mapping.
- Confirmed Android minimum SDK and final physical-device demo target.
- LLM provider, model, and backend-only API credentials.
- Decision on optional Python agentic AI.

No health advice produced by this project should be presented as medical diagnosis, prescription, or emergency care.
