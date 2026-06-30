# Food Photo Nutrition Analysis Plan

## Summary

This feature owns the end-to-end food photo nutrition analysis flow.

It does not implement RAG, Qdrant, embeddings, or AI Coach planning. The feature lets users take or choose a meal photo, sends the image to the backend, receives AI-estimated nutrition data, lets the user review and edit the result, then saves the confirmed meal into the Food tracker.

## Scope

### In Scope

- Camera-based meal photo capture.
- Gallery photo selection.
- Fast-mode image upload: resize to a maximum 1280 px side and JPEG quality 75 before analysis.
- Backend-only AI image analysis.
- Structured nutrition estimates:
  - food name
  - estimated grams
  - calories
  - protein
  - carbohydrates
  - fat
  - fiber
  - confidence
- Dedicated review screen before saving.
- User editing of detected food items.
- User deletion of incorrect detected items.
- Low-confidence warning without blocking save.
- Confirmed save into Food tracker.
- AI meal thumbnail storage and display.
- Automated backend and Android tests.
- One real-model manual demo before final PR.

### Out of Scope

- RAG.
- AI Coach.
- Qdrant.
- Embeddings.
- External nutrition knowledge retrieval.
- Automatic saving without user confirmation.
- Saving original full-size photos.
- Full nutrition-label fields such as sodium or sugar.
- Historical photo logging; first version only supports today.

## Current State

The repository already contains a working foundation:

- Backend `FoodImageAnalyzer` calls the OpenAI Responses API with image input and JSON schema output.
- Backend exposes `POST /api/food/analyze`.
- Backend supports confirmed AI food entries through `POST /api/food/entries/analyzed`.
- Android has CameraX capture flow.
- Android has `FoodViewModel.analyze(...)` and `confirmAnalysis(...)`.
- Android currently shows a basic photo estimate card on the Food screen.

The implementation should harden and extend the existing flow rather than rewrite it from scratch.

## Backend Plan

### Food Analysis

Keep `POST /api/food/analyze`.

Behavior:

- Accept multipart image upload.
- Reject empty files.
- Reject files larger than 10 MB.
- Accept valid image MIME types only.
- Call the configured backend-only vision-capable LLM.
- Use `store: false`.
- Use fast default analysis settings: low image detail, 800 max output tokens, and at most 10 detected items.
- Request strict JSON schema output.
- Return structured nutrition estimates.
- Return `503 FOOD_AI_NOT_CONFIGURED` when no API key is configured.
- Return `502 FOOD_AI_UNAVAILABLE` for provider failures.
- Return `502 FOOD_AI_INVALID_RESPONSE` for invalid model output.
- Return `422 FOOD_NOT_RECOGNIZED` when no food is detected.

### Confirmed AI Meal Save

Keep existing JSON endpoint:

```text
POST /api/food/entries/analyzed
```

Add a new multipart endpoint:

```text
POST /api/food/entries/analyzed/photo
```

Multipart fields:

- `entry`: edited `AnalyzedFoodEntryRequest` JSON
- `thumbnail`: compressed image file

Behavior:

- Save the confirmed meal and its food items.
- Save only a compressed thumbnail.
- Do not save the original image.
- Save thumbnail only for AI-analyzed meals.
- Perform meal save and thumbnail save in one transaction.
- Delete thumbnail automatically when the food entry is deleted.

### Thumbnail Retrieval

Add:

```text
GET /api/food/entries/{id}/thumbnail
```

Behavior:

- Require JWT.
- Only the owning user can access the thumbnail.
- Return `404` if the entry does not exist, belongs to another user, or has no thumbnail.
- Return image bytes with the correct content type.

### Database

Add a new Flyway migration, using the next available version.

Create table:

```sql
food_entry_photos
```

Suggested columns:

- `id`
- `food_entry_id`
- `content_type`
- `thumbnail`
- `created_at`

Constraints:

- `food_entry_id` references `food_entries(id)` with cascade delete.
- One thumbnail per food entry.
- Thumbnail is only created for AI photo analysis saves.

### API Model Changes

Add to `FoodEntryResponse`:

```json
{
  "photoThumbnailAvailable": true
}
```

Existing manual/catalog meals should return `false`.

## Android Plan

### Image Input

Food meal card should offer:

- `Take photo`
- `Choose photo`

Camera flow:

- Use existing CameraX implementation.
- Keep camera permission request.

Gallery flow:

- Use Android system Photo Picker.
- Do not request broad storage permissions.
- Read selected image bytes and send them to the same analysis flow.
- Compress camera and gallery images before upload to reduce latency.

### Review Screen

After analysis, navigate to a dedicated review screen.

The review screen shows:

- Meal type.
- Selected date, limited to today.
- Thumbnail preview.
- AI summary.
- Disclaimer.
- List of detected food items.
- Confidence per item.
- Low-confidence warning where relevant.

Each detected item can be edited:

- name
- grams
- calories
- protein
- carbohydrates
- fat
- fiber

Each detected item can be deleted.

Confidence is read-only.

Save button is enabled only when at least one item remains and numeric fields are valid.

### Save Flow

On confirm:

- Build edited `AnalyzedFoodEntryRequest`.
- Include compressed thumbnail.
- Send multipart request to `POST /api/food/entries/analyzed/photo`.
- Clear analysis state after success.
- Refresh the selected Food date.
- Navigate back to the Food screen.

On discard:

- Clear analysis state.
- Return to Food screen.

### Food Entry Display

Saved AI photo meals should show the thumbnail inside the Food entry card.

Manual/catalog meals do not need thumbnails.

## Safety Rules

- The Android app must never contain the LLM API key.
- The backend must be the only caller of the model API.
- Use `store: false`.
- Do not present nutrition values as exact facts.
- Show that AI estimates can be wrong.
- Require user confirmation before saving.
- Do not save original photos.
- Do not let AI failure break manual food logging.
- Do not log full images, full model responses, API keys, or sensitive user data.

## Test Plan

### Backend Unit Tests

Cover:

- Responses API request includes image data.
- Request includes `store: false`.
- Request uses JSON schema output.
- Valid model response parses into nutrition fields.
- Empty item list maps to `422 FOOD_NOT_RECOGNIZED`.
- Invalid model output maps to `502 FOOD_AI_INVALID_RESPONSE`.
- Missing API key maps to `503 FOOD_AI_NOT_CONFIGURED`.

### Backend Integration Tests

Cover:

- Authenticated user can analyze a valid image.
- Authenticated user can save edited AI photo meal with thumbnail.
- Saved AI meal appears in food entries with `photoThumbnailAvailable = true`.
- Owner can fetch thumbnail.
- Another user cannot fetch thumbnail.
- Deleting food entry deletes thumbnail.
- Manual/catalog food entries return `photoThumbnailAvailable = false`.

### Android ViewModel Tests

Cover:

- Analyze image success stores analysis state.
- Edit detected item updates pending save data.
- Delete detected item removes it from pending analysis.
- Low-confidence item shows warning state.
- Confirm sends edited items to repository.
- Confirm clears analysis state.
- Discard clears analysis state.
- Analyze failure shows stable error.

### Android UI / Build Verification

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest assembleDebug
```

### Backend Verification

Run:

```powershell
cd backend
.\mvnw.cmd test
```

### Final Repo Check

Run:

```powershell
git diff --check
```

## Manual Demo Checklist

Before final PR/demo:

1. Set backend `.env` with a real `LLM_API_KEY`.
2. Start backend stack.
3. Open Android app.
4. Go to Food.
5. Take a meal photo.
6. Confirm AI analysis returns food items and nutrition estimates.
7. Edit at least one value.
8. Delete one incorrect item if present.
9. Save.
10. Confirm saved meal appears in Food tracker.
11. Confirm thumbnail appears in the meal card.
12. Confirm manual food entry still works.
13. Confirm AI failure does not crash the app.

## PR Notes

The PR description should mention:

- This feature is separate from AI Coach and RAG.
- It does not implement Qdrant or embeddings.
- It uses backend-only AI credentials.
- It stores only thumbnails, not original photos.
- It requires user confirmation before saving AI estimates.
- Automated tests use mocks.
- Real model behavior was manually verified if a real API key was available.
