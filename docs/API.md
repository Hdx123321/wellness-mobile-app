# API Contract

All business endpoints are rooted at `/api`. JSON timestamps use ISO-8601 UTC. Record dates use `YYYY-MM-DD`.

## Implemented in stage 1

### Health

`GET /actuator/health`

Success:

```json
{
  "status": "UP"
}
```

## Authentication and onboarding

### Register a client

`POST /api/auth/register`

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "StrongPass123",
  "displayName": "Alice"
}
```

Registration creates only `CLIENT` accounts. Coach and administrator provisioning is not public.

### Login

`POST /api/auth/login`

The `identifier` accepts username or email. Both registration and login return a bearer access token, role, and `onboardingRequired` flag.

```json
{
  "identifier": "alice",
  "password": "StrongPass123"
}
```

### Onboarding

```text
GET /api/onboarding/questions
GET /api/onboarding/profile
PUT /api/onboarding/profile
POST /api/auth/logout
```

All onboarding endpoints require `Authorization: Bearer <token>`. The profile path never accepts a `userId`; ownership always comes from the verified JWT subject.

## Built-in trackers

```text
GET    /api/trackers/types
POST   /api/tracker-entries
GET    /api/tracker-entries?type=&from=&to=&page=0&size=20
GET    /api/tracker-entries/{id}
PUT    /api/tracker-entries/{id}
DELETE /api/tracker-entries/{id}
```

All endpoints require a bearer token and derive ownership from its subject.

| Type | `amount` meaning | Unit | Required detail |
|---|---|---|---|
| `FOOD` | Calories | `kcal` | Food or meal |
| `WEIGHT` | Body weight | `kg` | No |
| `WORKOUT` | Duration | `min` | Workout type |
| `STEPS` | Step count | `steps` | No; whole number only |
| `SLEEP` | Duration | `min` | No |
| `WATER` | Volume | `ml` | No |

Example entry:

```json
{
  "type": "WORKOUT",
  "recordedAt": "2026-06-28T06:00:00Z",
  "amount": 45,
  "detail": "Strength training",
  "notes": "Upper body"
}
```

## Frozen endpoint plan

```text
POST   /api/wellness-records
GET    /api/wellness-records?page=0&size=20&from=&to=
GET    /api/wellness-records/{id}
PUT    /api/wellness-records/{id}
DELETE /api/wellness-records/{id}
GET    /api/wellness-records/summary?days=7

POST   /api/chat/sessions
GET    /api/chat/sessions?page=0&size=20
GET    /api/chat/sessions/{id}/messages?page=0&size=50
POST   /api/chat/sessions/{id}/messages

POST   /api/recommendations/generate
GET    /api/recommendations?page=0&size=20
GET    /api/recommendations/latest
```

## Error envelope

```json
{
  "timestamp": "2026-06-27T04:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/wellness-records",
  "fieldErrors": {
    "sleepHours": "must be between 0 and 24"
  }
}
```

List responses will consistently contain `content`, `page`, `size`, `totalElements`, and `totalPages`.
