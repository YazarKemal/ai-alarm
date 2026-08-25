# PromptHaven Alarm — Design System

PromptHaven Alarm is the first native Android product in the PromptHavenAI ecosystem.
The product must feel recognizably PromptHaven without looking like a web page wrapped in an app.
Use Material 3 interaction conventions, PromptHaven brand tokens, large readable clocks, and restrained AI surfaces.

## Product boundary

This app is an AI-enhanced alarm clock only.
Do not add mail, calendar, files, photos, contacts, documents, sleep tracking, or other future ecosystem products into this application.
Future PromptHavenAI products should be separate Play Store apps that reuse the same account, design language, and backend foundations.

## Brand tokens

Canonical PromptHaven web identity:

- Gold 1: `#FFD700`
- Gold 2: `#FFBF00`
- Gold gradient: `135deg, #FFD700 -> #FFBF00`
- Brand black: `#050505`
- Dark surface: `#0D0D0D`
- Light background: `#FFFCF2`
- Light surface: `#FFFFFF`
- Primary dark text on gold: `#171300`

Never place white text on the bright gold gradient. Use near-black text/icons on gold for accessibility.
Gold is the signature accent, not a fill for every component.

## Visual character

- Premium, calm, precise, modern.
- Rounded Material 3 geometry; avoid excessive glassmorphism.
- Large clock numerals are the dominant visual element.
- Gold communicates action, focus, next alarm, and AI intelligence.
- Neutral surfaces carry lists and settings.
- Error/red is reserved for destructive or permission-critical states.
- Dark mode should be the strongest expression of the PromptHaven brand.

## Typography

Use Android system/Material typography for v1 to avoid font-loading complexity.
Clock numerals may use a tabular/monospaced treatment when available.
Hierarchy:

1. Clock / next alarm — display size.
2. Screen title — headline.
3. Alarm labels — title.
4. Metadata / repeat days — body.
5. Section labels — label style, uppercase only when short.

## Home screen

The home screen should contain, in order:

1. PromptHaven Alarm header with settings action.
2. Clock face / next-alarm hero.
3. AI suggestion surface only when there is something actionable.
4. Permission warnings only when required.
5. Alarm list.
6. Gold floating action button to add an alarm.

Do not permanently occupy home-screen space with AI status text such as provider names.
AI should appear as useful actions, not infrastructure branding.

## Clock faces

Users must be able to choose between analog and digital clocks. v1 clock styles:

### Digital
- `Digital Minimal` — large HH:mm, no seconds by default.
- `Digital Bold` — heavier numerals with next alarm beneath.
- `Digital Night` — dark, low-distraction bedside mode.

### Analog
- `Analog Classic` — 12 markers, hour/minute hands.
- `Analog Minimal` — four cardinal markers, no numerals.
- `Analog Gold` — gold minute accents on the PromptHaven black surface.

Clock style is presentation only; it must never affect alarm scheduling reliability.
Persist the selected style locally with DataStore.

## Alarm editor

The editor must remain deterministic and conventional even when AI exists.
Required controls:

- Time
- Label
- Repeat days
- Vibration
- Snooze duration
- Sound
- Save / delete

An AI natural-language entry point may sit above the conventional editor, e.g. “Hafta içi 07:15’te uyandır”.
The parsed result must always be shown to the user before saving.

## AI behavior

AI augments the alarm clock; it never owns alarm reliability.

v1 principles:

- Deterministic Android alarm scheduling remains fully offline.
- AI can parse natural-language alarm requests.
- AI can learn recurring wake patterns and suggest a likely forgotten alarm.
- New installs must not silently create alarms; autonomous creation requires explicit opt-in.
- LLM/provider API keys must never ship in the Android binary.
- Remote AI calls must go through a PromptHavenAI server-side proxy.
- A failed AI request must never prevent a manually configured alarm from firing.

## Ecosystem naming

Recommended Android application IDs:

- Alarm: `com.prompthavenai.alarm`
- Calendar: `com.prompthavenai.calendar`
- Mail: `com.prompthavenai.mail`
- Files: `com.prompthavenai.files`
- Photos: `com.prompthavenai.photos`
- Contacts: `com.prompthavenai.contacts`
- Docs: `com.prompthavenai.docs`
- Sleep: `com.prompthavenai.sleep`

Shared branding should be “PromptHaven” at the product level and “PromptHavenAI” at the ecosystem/company level.

## Store identity

Recommended store name: **PromptHaven Alarm — AI Alarm Clock**
Recommended in-app short name: **PromptHaven Alarm**

The icon should be recognizable at 48dp: a simple alarm-clock silhouette or clock ring using the gold gradient on near-black, without small text.

## Implementation rules

- Jetpack Compose + Material 3.
- Preserve the current local-first Room/AlarmManager architecture.
- Dynamic Android colors remain disabled so PromptHaven branding is stable.
- Support light, dark, and system theme.
- Minimum touch target: 48dp.
- Avoid decorative animation during ringing; ringing UI prioritizes immediate Snooze and Dismiss actions.
- Treat alarm reliability, accessibility, and lock-screen behavior as release blockers.
