# Product Backlog

## Delivery slices

### B1 - Authentication and first-login onboarding

- Client registration and JWT login.
- Stable onboarding question catalog, mostly single-choice or multiple-choice.
- Private user profile with date of birth, body measurements, optional ethnicity, weight goal, routine, activity level, exercise preferences, and core needs.
- Login response tells Android whether onboarding is required.

### B2 - User-defined trackers

- Completed: built-in food, weight, workout, steps, sleep, and water definitions.
- Completed: tracker entries with timestamp, amount, detail, note, source, pagination, filtering, and ownership checks.
- Later: user-created tracker definitions, optional targets, display order, and dashboard selection.
- Completed on Android: dashboard, add/edit form, filtered history, deletion confirmation, and refresh/error states for all six built-ins.

### B3 - Device capabilities and reminders

- Device registration and permission/capability status reported by Android.
- Reminder definitions synchronized by the backend; Android schedules local alarms and notifications.
- Health Connect import endpoints for steps, exercise sessions, sleep, and other user-approved records.
- Camera media metadata and authenticated upload workflow.
- SMS uses a user-confirmed system Intent or an approved backend provider; the app will not request restricted SMS permissions by default.

### B4 - Coach/client relationship and realtime messaging

- Administrator-created coach accounts and client/coach assignments.
- Client and coach inboxes scoped to their assignments.
- Persisted messages, unread counts, and WebSocket/STOMP delivery.
- Reuse only the reference project's generic realtime patterns; replace merchant/customer trust assumptions with JWT-derived coach/client ownership.

### B5 - AI and recommendations

- AI chat uses recent tracker/profile summaries as controlled context.
- Camera-assisted daily check-in remains advisory and never claims diagnosis.
- Sensitive profile fields are excluded unless the user later gives explicit purpose-specific consent.

## Platform boundary

Android owns runtime permission dialogs, CameraX, Health Connect authorization, notification permission, and local alarm scheduling. The backend owns authenticated data, reminder definitions, device capability records, media authorization, coach relationships, and message persistence.
