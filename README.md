# AI Alarm

A production-quality, local-first Android alarm-clock application. The long-term
product is an AI-powered smart alarm; this milestone builds the reliable native
alarm foundation that AI features will later extend.

## Current milestone

**v0.1 — Native Alarm Core**

No AI provider is integrated yet. The focus is alarm reliability, correct
Android lifecycle/background behavior, clean architecture, and persistence.

## Features currently implemented

- Create, edit, delete, enable, and disable alarms
- Persistent storage via Room (reactively observed by the UI)
- Exact alarm scheduling via `AlarmManager.setAlarmClock`
- Repeating alarms (weekday selection) and one-time alarms
- Next-occurrence calculation that is timezone/DST aware
- Foreground ringing service with system alarm sound and vibration
- High-priority alarm notification with Snooze / Dismiss actions
- Full-screen ringing activity over the lock screen
- Exact-alarm, notification, and full-screen-intent permission handling
- Reboot and time/timezone-change rescheduling
- Light / Dark / System Material 3 theme with dynamic color on Android 12+
- Debug-only "test alarm in 30 seconds" quick-test control

## Architecture

The app uses a lightweight manual dependency container (`AppContainer`) — no Hilt
yet — with a straightforward UI / Domain / Data split.

```
UI (Compose)  ->  ViewModel  ->  Domain (AlarmRepository, Alarm)  ->  Data (Room, DataStore)
                                      |
                              Core alarm engine (scheduling / ringing)
```

- **UI** — Jetpack Compose + Material 3. Screens: Home, Alarm Editor, Ringing.
  No Room access or alarm logic lives in Composables.
- **Domain** — immutable `Alarm` model and `AlarmRepository` interface.
- **Data** — Room (`AlarmEntity`, `AlarmDao`, `AiAlarmDatabase`) and DataStore
  preferences. Room stores alarms; DataStore stores simple preferences.
- **Alarm system** — `core/alarm` contains the scheduler, receiver, ringing
  service, notification manager, intent factory, next-alarm calculator, and the
  ringing lifecycle controller.

The alarm engine is fully offline and has no AI concerns inside it. Future AI
features (morning briefing, smart snooze, etc.) can live in a separate module
(`feature/assistant`) without rewriting the core engine.

## Android alarm flow

```
User saves Alarm
       ↓
Room (persist)
       ↓
AlarmScheduler
       ↓
AlarmManager.setAlarmClock
       ↓
AlarmReceiver  (ACTION_START_ALARM)
       ↓
AlarmController  (loads alarm, starts service)
       ↓
AlarmRingingService  (foreground: sound + vibration)
       ↓
Alarm notification  (high priority, full-screen intent, Snooze/Dismiss)
       ↓
AlarmRingingActivity  (full-screen UI)
```

When a repeating alarm fires, its next occurrence is scheduled; a one-time alarm
is disabled after firing. Snooze schedules a separate one-shot occurrence and
never mutates the recurring configuration.

## Required Android permissions

| Permission | Why |
| --- | --- |
| `USE_EXACT_ALARM` | Auto-granted for alarm-clock apps on Android 13+ (API 33+). The app checks `AlarmManager.canScheduleExactAlarms()` and degrades gracefully if unavailable. |
| `SCHEDULE_EXACT_ALARM` (maxSdk 32) | Backward compatibility for Android 12 / 12L (API 31-32), where `USE_EXACT_ALARM` does not exist. Scoped with `maxSdkVersion=32` so it is not declared on Android 14+. |
| `POST_NOTIFICATIONS` | Show the ringing notification (Android 13+ runtime permission). |
| `USE_FULL_SCREEN_INTENT` | Launch the full-screen ringing activity. On Android 14+ the user can revoke this; capability is checked before use. |
| `RECEIVE_BOOT_COMPLETED` | Reschedule enabled alarms after reboot. |
| `FOREGROUND_SERVICE` | Run the ringing service in the foreground. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required subtype for audible playback foreground services on Android 14+. |
| `VIBRATE` | Vibrate when the alarm fires. |

### Exact-alarm strategy

AI Alarm is a genuine alarm-clock app, so it uses the Play-recommended
`USE_EXACT_ALARM` permission on Android 13+ (API 33+), which is auto-granted and
non-revocable. For Android 12 / 12L (API 31-32) — where `USE_EXACT_ALARM` does
not exist — it keeps `SCHEDULE_EXACT_ALARM` scoped via `maxSdkVersion="32"`.
Scheduling always uses `AlarmManager.setAlarmClock()`, which is exempt from the
exact-alarm permission, so the permission settings flow is only surfaced on
API 31-32. The decision is isolated in
`core/permission/ExactAlarmPermissionManager.kt`.

## Running locally

Requirements: JDK 17+ (21 works), Android SDK with a recent platform.

```bash
./gradlew test            # unit tests
./gradlew assembleDebug   # debug APK
./gradlew bundleRelease   # release AAB
```

The debug APK is written to `app/build/outputs/apk/debug/`. The release AAB is
written to `app/build/outputs/bundle/release/`.

## Release build & signing

Signing credentials are **never** stored in the repository. To sign a release,
create a gitignored `keystore.properties` in the project root (or export the
same keys as environment variables):

```properties
RELEASE_STORE_FILE=C:/path/to/upload.keystore
RELEASE_STORE_PASSWORD=********
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=********
```

If no credentials are present, `bundleRelease` / `assembleRelease` **fails**
with a clear error before producing an unsigned artifact; debug and unit-test
builds are unaffected. Upload the signed AAB to Google Play Console using Play
App Signing (provide your upload keystore there; Google Play then manages the
app signing key).

The local `DEEPSEEK_API_KEY` (optional, for testing the AI assistant) is read
from `local.properties` and compiled **only** into debug builds; release builds
never embed a developer key and fall back to on-device heuristics.

## Testing alarms

Manual test procedure on a physical device:

1. Create an alarm 2 minutes in the future.
2. Lock the phone.
3. Wait for the trigger time.
4. Confirm the full-screen ringing activity, notification, sound, and vibration.
5. Test Dismiss — everything stops and the notification clears.
6. Test Snooze — ringing stops, then rings again after the snooze interval.
7. Reboot the device and confirm the alarm remains scheduled.
8. Swipe the app away from recents and confirm the alarm still fires.
9. Deny notifications / exact alarms / full-screen access and confirm the app
   degrades gracefully (banners on the Home screen, no crash).

Debug quick-test: in a debug build, tap the timer icon in the top bar to
schedule a test alarm 30 seconds out.

## Future roadmap

Not implemented in v0.1 (architecture is left open for them):

- AI morning briefing
- Adaptive alarm difficulty
- Sleep/wake pattern analysis
- Smart snooze
- Weather-aware wake briefing
- Calendar integration
- Wake-up challenges
- Statistics
- Cloud backup
