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

## Android client integration (App Check)

The app attaches a real Firebase App Check token to each AI-proxy request as
the `x-firebase-app-check` header (see `AppCheckTokenProvider` /
`PromptHavenAiAlarmInterpreter`). No AI provider secret ever lives on device.

**Firebase Console steps you must perform before a production build:**

1. Create/select your Firebase project.
2. **Register the Android app** `com.prompthavenai.alarm` (add the release
   SHA-1 fingerprint under Project settings → Your apps).
3. **App Check** → Apps → `com.prompthavenai.alarm` → Enforce → enable **Play
   Integrity** provider (no API key/secret needed).
4. **Project settings → Your apps → Download `google-services.json`** and place
   it at `app/google-services.json`. The Google Services Gradle plugin is
   applied automatically the moment that file exists; until then the app builds
   fine without it and App Check is skipped (AI degrades to offline).
5. Deploy the backend with `APP_CHECK_ENFORCED=true` (see above).

**Debug / local App Check token (only for testing against an enforcing
backend):** enable the **Debug** provider in the App Check console to get a
debug token, then add
`FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())`
in `AiAlarmApplication` for debug builds. In normal local development you do
not need this — run the emulator with `APP_CHECK_ENFORCED` unset so no token is
required.

### Local Functions emulator + Android emulator

1. Start the Functions emulator:
   ```bash
   firebase emulators:start --only functions
   ```
   (Backend runs with App Check enforcement disabled by default.)
2. Point the app at the emulator by building debug with your project id:
   ```bash
   ./gradlew :app:assembleDebug -PPROMPTHAVEN_FIREBASE_PROJECT_ID=<PROJECT_ID>
   ```
   This produces the debug base URL `http://10.0.2.2:5001/<PROJECT_ID>/us-central1`
   (`10.0.2.2` is the Android emulator's alias for the host's `localhost`).
   Or override the whole URL with `-PPROMPTHAVEN_FUNCTIONS_DEBUG_URL=...`.
   A blank/unconfigured value leaves AI "not configured" — nothing is sent to an
   invalid URL.
