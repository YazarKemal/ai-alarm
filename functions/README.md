# PromptHaven Alarm — AI proxy (Firebase Cloud Functions, Gen 2)

Server-side `interpretAlarmRequest` endpoint. This is the ONLY place an AI
provider key exists. The Android app holds no provider secret and only ever
calls this public HTTPS endpoint.

## Contract

**Request** (POST, JSON):
```json
{ "prompt": "wake me at 7 on weekdays" }
```

**Response** — prefill for the app's editor (the app's SAVE button schedules;
this backend never schedules anything):
```json
{ "status": "ok", "alarm": { "hour": 7, "minute": 0, "repeatDays": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"] } }
```

Or, when ambiguous:
```json
{ "status": "clarification", "clarification": "Which day should this repeat?" }
```

## Security
- Provider key: set the Firebase Secret `PROVIDER_API_KEY` (see below). It is
  never bundled into the function or the APK.
- Prompts capped at 500 chars. Full prompts are never logged.
- Provider JSON is strictly schema- and range-checked (`sanitize`) before
  returning — raw AI output is never trusted.
- Backend cannot schedule alarms by construction: it returns values only.

## Deploy (production — do NOT do without explicit approval)
```bash
firebase login
firebase functions:secrets:set PROVIDER_API_KEY
firebase deploy --only functions
```

Then update the app's `PROMPTHAVEN_FUNCTIONS_BASE_URL` in
`AiAlarmInterpreter.kt` to the deployed function's region/project URL.
