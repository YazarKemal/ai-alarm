# PromptHaven Alarm — AI proxy (Firebase Cloud Functions, Gen 2)

Server-side `interpretAlarmRequest` endpoint. This is the ONLY place an AI
provider key exists. The Android app holds no provider secret and only ever
calls this public HTTPS endpoint.

## Contract

**Request** (POST, JSON) — the backend resolves relative dates like "tomorrow"
using `timezone`, `locale` and `currentDateTime`:

```json
{
  "text": "wake me tomorrow at 7:30 and call it Workout",
  "timezone": "Europe/Istanbul",
  "locale": "tr-TR",
  "currentDateTime": "2026-08-25T06:00:00+03:00"
}
```

All fields are required; `text` is capped at 500 chars.

**Response — success** (the app prefills its editor; the app's SAVE button
schedules, this backend never schedules anything):

```json
{
  "status": "success",
  "interpretation": {
    "time": "07:30",
    "date": "2026-08-26",
    "repeatDays": [],
    "label": "Workout"
  },
  "needsClarification": false,
  "clarificationQuestion": null
}
```

- `repeatDays` empty + a `date` => a one-time alarm on that calendar day.
- `repeatDays` non-empty + `date: null` => a repeating alarm.
- `date` and `repeatDays` are never both set.

**Response — clarification needed** (instead of guessing):

```json
{
  "status": "clarification_required",
  "interpretation": null,
  "needsClarification": true,
  "clarificationQuestion": "Which day should this repeat?"
}
```

Provider output is strictly schema- and range-checked (`sanitize`) before
returning — raw AI JSON is never trusted.

## Security

- **Provider key**: set the Firebase Secret `PROVIDER_API_KEY` (see below). It
  is never bundled into the function or the APK.
- **Text capped** at 500 chars. Full prompts are never logged.
- **Backend cannot schedule alarms by construction**: it returns values only.
- **App Check (Play Integrity)**: the request must carry an App Check token in
  the `x-firebase-app-check` header. Enforcement is gated by the
  `APP_CHECK_ENFORCED` env var:
  - `APP_CHECK_ENFORCED=true` (production): every request without a valid token
    is rejected (`403` when absent, `401` when invalid).
  - unset/false (local / emulator): requests without a token are allowed so
    development stays practical. **Set it to `true` before production deploy.**
- **Rate limiting**: a lightweight in-memory per-IP token bucket (default 60
  requests/min, tunable via `RATE_LIMIT_PER_MINUTE`). This is per function
  instance; for multi-instance production scale-out, replace with a distributed
  store (e.g. Cloud Memorystore) or Cloud Armor.

## Local development / emulator

With the Functions emulator running, the app talks to
`http://10.0.2.2:5001` (Android emulator -> host). No App Check token is
needed because `APP_CHECK_ENFORCED` defaults to false.

## Deploy (production — do NOT do without explicit approval)

```bash
firebase login
firebase use --add          # choose/alias your project
firebase functions:secrets:set PROVIDER_API_KEY
# Ensure APP_CHECK_ENFORCED=true is set as a runtime env for the deployed function.
firebase deploy --only functions --project <PROJECT_ID>
```

Then supply the deployed function's base URL at app build time via the
**public** Gradle property:

```bash
./gradlew :app:assembleRelease \
  -PPROMPTHAVEN_FUNCTIONS_URL=https://<region>-<project>.cloudfunctions.net
```

The base URL is public (not a secret) and is intentionally **not** hard-coded
in the APK; the AI proxy shows "not configured" until it is supplied.
