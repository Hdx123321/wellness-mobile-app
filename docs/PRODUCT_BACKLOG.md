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
- Completed: dedicated food tracker with a seeded food catalog, serving-based macro and fiber calculations, daily totals, calendar, and chart.
- Completed: CameraX food capture and backend-only AI image analysis with explicit review before persistence.
- Later: user-created tracker definitions, optional targets, display order, and dashboard selection.
- Completed on Android: Home, per-tracker calendar, seven-day chart, selected-day data, and today-only editing for all six built-ins.
- Completed on Android: Home health summary and private health profile with latest tracked weight, BMI, estimated basal metabolism, moderate-intensity heart-rate zone, and target-weight progress.
- Completed on Android: scrollable height editing and direct Health Profile to Weight Tracker navigation.

### B3 - Device capabilities and reminders

- Completed: avatar account page with profile management, daily local-notification reminder settings, and logout.
- Completed: global date picker shared by Home, tracker details, and Food.
- Device registration and permission/capability status reported by Android.
- Reminder definitions synchronized by the backend; Android schedules local alarms and notifications.
- Health Connect import endpoints for steps, exercise sessions, sleep, and other user-approved records.
- Camera media metadata and authenticated upload workflow.
- SMS uses a user-confirmed system Intent or an approved backend provider; the app will not request restricted SMS permissions by default.

### B4 - Coach/client relationship and realtime messaging

- Completed: automatic client assignment to an administratively provisioned coach.
- Completed: client and coach conversations scoped to their JWT identity.
- Completed: persisted messages and three-second incremental polling on Android.
- Later: administrator provisioning UI, unread counts, and WebSocket/STOMP delivery.
- Reuse only the reference project's generic realtime patterns; replace merchant/customer trust assumptions with JWT-derived coach/client ownership.

### B5 - AI and recommendations

- Completed: persisted AI advisor chat using recent tracker/profile summaries as controlled context and backend-only Responses API credentials.
- Camera-assisted daily check-in remains advisory and never claims diagnosis.
- Sensitive profile fields are excluded unless the user later gives explicit purpose-specific consent.

## Platform boundary

Android owns runtime permission dialogs, CameraX, Health Connect authorization, notification permission, and local alarm scheduling. The backend owns authenticated data, reminder definitions, device capability records, media authorization, coach relationships, and message persistence.
