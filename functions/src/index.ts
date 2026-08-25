/**
 * PromptHaven AI proxy V2 — `interpretAlarmRequest`
 *
 * Two-stage architecture: the AI model ONLY extracts structured constraints from
 * natural language; deterministic code computes the actual schedule (see
 * planner.ts). The model is never allowed to invent final alarm times.
 *
 * The ONLY place an AI provider key exists is server-side secret management
 * (Firebase Secret Manager / env: PROVIDER_API_KEY). The Android app sends a
 * public HTTPS request with the user's text, non-secret context, and the local
 * planning preferences needed for the current request, and receives back either:
 *   - a QUICK_ALARM (a single, direct alarm time), or
 *   - a GOAL_PLAN (computed wake plan with up to 3 alarms), or
 *   - CLARIFICATION_REQUIRED (missing critical info, phrased in the user's locale).
 *
 * Clarifications are STRUCTURED and anti-loop:
 *   - The client sends `clarifications: [{ code, question, answer }]`. The
 *     backend knows exactly which answer belongs to which missing value, so a
 *     valid commute answer is consumed deterministically and planning proceeds
 *     immediately — the same generic question is never re-asked.
 *   - Provider messages always follow the semantic order:
 *       SYSTEM, USER original request, [ASSISTANT question, USER answer] per
 *       clarification.
 *   - Known clarification codes (commute_required, commute_minutes_required)
 *     are LOCALIZED on the client; the backend `clarificationQuestion` string is
 *     only a fallback for unknown codes.
 *   - Locale is normalized to a canonical BCP-47 primary tag (tr-TR -> tr,
 *     en-US -> en, pt-BR stays pt-BR) so questions never fall back to English
 *     by accident.
 *
 * Security posture:
 *   - Provider key via secret; model via PROVIDER_MODEL env (default
 *     deepseek-v4-flash). Never exposed to Android.
 *   - Provider model runs in NON-THINKING mode (structured extraction doesn't
 *     need long reasoning) for lower latency/cost.
 *   - Text capped. No full user prompts are logged. Requests are not stored.
 *   - Provider output is strictly schema- and range-checked (untrusted).
 *   - Backend NEVER schedules an alarm; the app's CREATE PLAN/SAVE is the only
 *     thing that schedules.
 *   - Firebase App Check verification defaults ON for deployed functions;
 *     explicit local/emulator bypass via APP_CHECK_ENFORCED=false.
 */
import { initializeApp } from "firebase-admin/app";
import { getAppCheck } from "firebase-admin/app-check";
import * as https from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { defineSecret } from "firebase-functions/params";
import { plan, MIN_MS } from "./planner";
import {
  normalizeLocale,
  parseClarifications,
  buildProviderTurns,
  resolveCommute,
  clarificationQuestion,
  COMMUTE_MIN,
  COMMUTE_MAX,
  MAX_TEXT_LENGTH,
  type Clarification,
  type ClarificationCode,
} from "./logic";

initializeApp();

const PROVIDER_API_KEY = defineSecret("PROVIDER_API_KEY");
const PROVIDER_ENDPOINT = process.env.PROVIDER_ENDPOINT ?? "https://api.deepseek.com/chat/completions";
const PROVIDER_MODEL = process.env.PROVIDER_MODEL ?? "deepseek-v4-flash";

// App Check enforcement defaults ON (deployed). Local/emulator dev sets
// APP_CHECK_ENFORCED=false to bypass. CORS is never treated as security.
const APP_CHECK_ENFORCED = process.env.APP_CHECK_ENFORCED !== "false";
const RATE_LIMIT_PER_MINUTE = Number(process.env.RATE_LIMIT_PER_MINUTE ?? 60);

const APP_CHECK_HEADER = "x-firebase-app-check";
const RATE_WINDOW_MS = 60_000;

const MAX_LABEL_LENGTH = 80;
const MAX_PLAN_ALARMS = 3;

// -------- hard validation limits --------
const SLEEP_MIN = 240, SLEEP_MAX = 720;
const PREP_MIN = 0, PREP_MAX = 360;
const BUFFER_MIN = 0, BUFFER_MAX = 180;
const PRE_BACKUP_MIN = 0, PRE_BACKUP_MAX = 60;

/** In-memory per-IP sliding-window limiter (per function instance). */
const buckets = new Map<string, { count: number; resetAt: number }>();
function isRateLimited(ip: string): boolean {
  const now = Date.now();
  const bucket = buckets.get(ip);
  if (!bucket || now >= bucket.resetAt) {
    buckets.set(ip, { count: 1, resetAt: now + RATE_WINDOW_MS });
    return false;
  }
  bucket.count++;
  return bucket.count > RATE_LIMIT_PER_MINUTE;
}

async function enforceAppCheck(req: https.Request): Promise<{ ok: true } | { ok: false; code: number; error: string }> {
  if (!APP_CHECK_ENFORCED) return { ok: true };
  const token = req.headers[APP_CHECK_HEADER];
  if (typeof token !== "string" || token.length === 0) {
    return { ok: false, code: 403, error: "app_check_required" };
  }
  try {
    await getAppCheck().verifyToken(token);
    return { ok: true };
  } catch (err) {
    logger.warn("App Check token verification failed", err);
    return { ok: false, code: 401, error: "invalid_app_check_token" };
  }
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface Preferences {
  targetSleepMinutes: number;
  preparationMinutes: number;
  commuteMinutes: number | null;
  bufferMinutes: number;
  wakePreference: string;
  preAlarmEnabled: boolean;
  preAlarmMinutes: number;
  backupAlarmEnabled: boolean;
  backupAlarmMinutes: number;
}

interface Extracted {
  intent: "arrive_by" | "wake_at";
  destinationLabel: string | null;
  targetDate: string; // yyyy-MM-dd (always resolved; else clarification)
  targetTime: string; // HH:mm
  requestedSleepMinutes: number | null;
  commuteMinutes: number | null;
  preparationMinutes: number | null;
}

type PlanResponse =
  | { status: "success"; kind: "quick_alarm"; interpretation: { time: string; date: string | null; repeatDays: string[]; label: string }; needsClarification: false; clarificationQuestion: null }
  | {
      status: "success";
      kind: "goal_plan";
      plan: {
        destinationLabel: string;
        targetTime: string;
        sleepStartTime: string;
        wakeTime: string;
        leaveByTime: string;
        sleepShortfallMinutes: number;
        assumptions: {
          sleepMinutes: number;
          preparationMinutes: number;
          commuteMinutes: number | null;
          bufferMinutes: number;
        };
        alarms: Array<{ time: string; date: string; label: string; role: string; enabled: boolean }>;
      };
      needsClarification: false;
      clarificationQuestion: null;
    }
  | { status: "clarification_required"; clarificationCode: ClarificationCode | null; clarificationQuestion: string; needsClarification: true };

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** "HH:mm" strictly in 0-23 / 0-59. */
function parseHm(value: unknown): { hour: number; minute: number } | null {
  if (typeof value !== "string") return null;
  const m = /^(\d{1,2}):(\d{2})$/.exec(value.trim());
  if (!m) return null;
  const hour = Number(m[1]);
  const minute = Number(m[2]);
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  return { hour, minute };
}

/** Valid ISO-8601 local date (yyyy-MM-dd). */
function parseIsoDate(value: unknown): string | null | undefined {
  if (value === null || value === undefined) return null;
  if (typeof value !== "string") return undefined;
  const s = value.trim();
  if (s.length === 0) return null;
  return /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(`${s}T00:00:00Z`)) ? s : undefined;
}

function clampInt(value: unknown, fallback: number, min: number, max: number): number {
  const n = typeof value === "number" && Number.isFinite(value) ? Math.round(value) : NaN;
  return Number.isNaN(n) ? fallback : Math.min(max, Math.max(min, n));
}

/** Reads an optional bounded minutes value, or null when absent/invalid. */
function optInt(value: unknown, min: number, max: number): number | null {
  const n = typeof value === "number" && Number.isFinite(value) ? Math.round(value) : NaN;
  if (Number.isNaN(n)) return null;
  if (n < min || n > max) return null;
  return n;
}

function parsePreferences(raw: unknown): Preferences {
  const p = (raw ?? {}) as Record<string, unknown>;
  const sleep = clampInt(p.targetSleepMinutes, 480, SLEEP_MIN, SLEEP_MAX);
  const prep = clampInt(p.preparationMinutes, 30, PREP_MIN, PREP_MAX);
  const buffer = clampInt(p.bufferMinutes, 15, BUFFER_MIN, BUFFER_MAX);
  const preMinutes = clampInt(p.preAlarmMinutes, 10, PRE_BACKUP_MIN, PRE_BACKUP_MAX);
  const backupMinutes = clampInt(p.backupAlarmMinutes, 10, PRE_BACKUP_MIN, PRE_BACKUP_MAX);
  // Saved commute is optional (null = unknown, then we must ask for arrive-by).
  const commute = optInt(p.commuteMinutes, COMMUTE_MIN, COMMUTE_MAX);
  return {
    targetSleepMinutes: sleep,
    preparationMinutes: prep,
    commuteMinutes: commute,
    bufferMinutes: buffer,
    wakePreference: p.wakePreference === "BALANCED" || p.wakePreference === "EARLY" ? p.wakePreference : "LATEST_POSSIBLE",
    preAlarmEnabled: p.preAlarmEnabled !== false,
    preAlarmMinutes: preMinutes,
    backupAlarmEnabled: p.backupAlarmEnabled !== false,
    backupAlarmMinutes: backupMinutes,
  };
}

/** Resolves an absolute epoch for a local date+time at the request's offset. */
function arrivalEpochMs(dateStr: string, timeStr: string, offsetMinutes: number): number | null {
  const hm = parseHm(timeStr);
  const date = parseIsoDate(dateStr);
  if (!hm || date === undefined || date === null) return null;
  const [y, mo, d] = date.split("-").map(Number);
  // Represent local time at the given offset as an absolute epoch.
  return Date.UTC(y, mo - 1, d, hm.hour, hm.minute) - offsetMinutes * MIN_MS;
}

/** Builds the system prompt so the model extracts constraints, never times. */
function buildExtractionPrompt(
  timezone: string,
  locale: string,
  currentDateTime: string,
  prefs: Preferences,
  activeClarification: Clarification | null
): string {
  const lines = [
    "You are the Planner for the Smart Alarm Clock app. You extract structured constraints from the user's natural language. You never compute final alarm times — code does that.",
    `Reference timestamp (ISO-8601): ${currentDateTime}`,
    `User timezone: ${timezone}`,
    `User locale: ${locale}`,
    "Resolve relative dates (tomorrow, today, 'Cuma') to an absolute target date using the reference timestamp.",
    "Preferences the user has configured (defaults; only override when the user EXPLICITLY states otherwise):",
    JSON.stringify({
      targetSleepMinutes: prefs.targetSleepMinutes,
      preparationMinutes: prefs.preparationMinutes,
      commuteMinutes: prefs.commuteMinutes,
      bufferMinutes: prefs.bufferMinutes,
      wakePreference: prefs.wakePreference,
    }),
    "",
    "Return exactly one JSON object, one of two shapes:",
    "1) When you can extract a goal with a target time:",
    `{"intent":"arrive_by","destinationLabel":"School","targetDate":"YYYY-MM-DD","targetTime":"HH:mm","requestedSleepMinutes":null,"commuteMinutes":null,"preparationMinutes":null}`,
    "2) When the user asks for a plain wake-up alarm at a specific time:",
    `{"intent":"wake_at","destinationLabel":"Wake Up","targetDate":"YYYY-MM-DD","targetTime":"HH:mm","requestedSleepMinutes":null,"commuteMinutes":null,"preparationMinutes":null}`,
    "Rules:",
    "- 'arrive_by' is for requests like 'be at school by 13:00' or 'okulda olmam lazım'. 'wake_at' is for 'wake me at 7:30'.",
    "- Only set commuteMinutes / preparationMinutes / requestedSleepMinutes when the user EXPLICITLY gives that number (in the request OR a prior clarification answer in conversation). NEVER guess or assume them; use null when unknown.",
    "- commuteMinutes is in minutes; null if not stated.",
    "- Use 24-hour time. If you cannot resolve a date or time, reply instead:",
    `{"clarificationQuestion":"one short question in the user's language to disambiguate"}`,
    "Do not include anything except the JSON.",
  ];
  if (activeClarification) {
    lines.push(
      "",
      "The assistant previously asked the user a clarifying question, and the user answered:",
      `Assistant question: "${activeClarification.question}"`,
      `User answer: "${activeClarification.answer}"`,
      "- If the user's answer expresses a travel duration (commute), set commuteMinutes to that duration in minutes.",
      "- Otherwise leave commuteMinutes null.",
      "- Do NOT ask for this clarification again."
    );
  }
  return lines.join("\n");
}

// ---------------------------------------------------------------------------
// Provider call (DeepSeek v4 flash, non-thinking)
// ---------------------------------------------------------------------------

async function callProvider(systemPrompt: string, turns: Array<{ role: string; content: string }>, apiKey: string): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  const messages: Array<{ role: string; content: string }> = [{ role: "system", content: systemPrompt }, ...turns];
  try {
    const response = await fetch(PROVIDER_ENDPOINT, {
      method: "POST",
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: PROVIDER_MODEL,
        response_format: { type: "json_object" },
        thinking: { type: "disabled" }, // structured extraction needs no long reasoning
        messages,
        max_tokens: 400,
      }),
    });
    if (!response.ok) throw new Error(`provider status ${response.status}`);
    const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
    return data.choices?.[0]?.message?.content ?? "";
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// Validation of the model's extracted constraints (untrusted)
// ---------------------------------------------------------------------------

function sanitizeExtraction(raw: string): { clarificationQuestion: string } | { extracted: Extracted } | null {
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }

  if (typeof obj.clarificationQuestion === "string" && obj.clarificationQuestion.trim()) {
    return { clarificationQuestion: obj.clarificationQuestion.trim().slice(0, 200) };
  }

  const intent = obj.intent;
  if (intent !== "arrive_by" && intent !== "wake_at") return null;
  const targetTime = typeof obj.targetTime === "string" ? obj.targetTime.trim() : "";
  if (!parseHm(targetTime)) return null;
  const date = parseIsoDate(obj.targetDate);
  if (date === undefined || date === null) return null;

  return {
    extracted: {
      intent,
      destinationLabel: typeof obj.destinationLabel === "string" ? obj.destinationLabel.trim().slice(0, MAX_LABEL_LENGTH) || null : null,
      targetDate: date,
      targetTime,
      requestedSleepMinutes: optInt(obj.requestedSleepMinutes, SLEEP_MIN, SLEEP_MAX),
      commuteMinutes: optInt(obj.commuteMinutes, COMMUTE_MIN, COMMUTE_MAX),
      preparationMinutes: optInt(obj.preparationMinutes, PREP_MIN, PREP_MAX),
    },
  };
}

// ---------------------------------------------------------------------------
// Endpoint
// ---------------------------------------------------------------------------

export const interpretAlarmRequest = https.onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 20,
    secrets: [PROVIDER_API_KEY],
    cors: true,
  },
  async (req, res) => {
    try {
      const ip = typeof req.headers["x-forwarded-for"] === "string"
        ? req.headers["x-forwarded-for"].split(",")[0].trim()
        : "unknown";
      if (isRateLimited(ip)) {
        res.status(429).json({ error: "rate_limited" });
        return;
      }

      const appCheck = await enforceAppCheck(req);
      if (!appCheck.ok) {
        res.status(appCheck.code).json({ error: appCheck.error });
        return;
      }

      if (req.method !== "POST") {
        res.status(405).json({ error: "method_not_allowed" });
        return;
      }

      const body = req.body ?? {};
      const text = typeof body.text === "string" ? body.text.slice(0, MAX_TEXT_LENGTH) : "";
      const timezone = typeof body.timezone === "string" ? body.timezone : "";
      const locale = typeof body.locale === "string" ? body.locale : "";
      const currentDateTime = typeof body.currentDateTime === "string" ? body.currentDateTime : "";
      const preferences = parsePreferences(body.preferences);
      const clarifications = parseClarifications(body.clarifications);

      if (text.trim().length === 0) {
        res.status(400).json({ error: "empty_text" });
        return;
      }
      if (timezone.trim().length === 0 || locale.trim().length === 0 || currentDateTime.trim().length === 0) {
        res.status(400).json({ error: "missing_context" });
        return;
      }

      const normalizedLocale = normalizeLocale(locale);
      const offset = offsetMinutes(currentDateTime);

      // The commute is the only value we solicit. Pick the most recent commute
      // clarification so a structured answer is consumed deterministically.
      const commuteClarifications = clarifications.filter(
        (c) => c.code === "commute_required" || c.code === "commute_minutes_required"
      );
      const activeClarification = commuteClarifications[commuteClarifications.length - 1] ?? null;

      const turns = buildProviderTurns(text, clarifications);
      const providerResult = await callProvider(
        buildExtractionPrompt(timezone, normalizedLocale, currentDateTime, preferences, activeClarification),
        turns,
        PROVIDER_API_KEY.value()
      );

      const parsed = sanitizeExtraction(providerResult);
      if (parsed === null) {
        res.status(502).json({ error: "invalid_provider_response" });
        return;
      }
      if ("clarificationQuestion" in parsed) {
        res.json({
          status: "clarification_required",
          clarificationCode: null,
          clarificationQuestion: parsed.clarificationQuestion,
          needsClarification: true,
        } satisfies PlanResponse);
        return;
      }

      const extraction = parsed.extracted;

      if (extraction.intent === "wake_at") {
        res.json({
          status: "success",
          kind: "quick_alarm",
          interpretation: {
            time: extraction.targetTime,
            date: extraction.targetDate,
            repeatDays: [],
            label: extraction.destinationLabel ?? "",
          },
          needsClarification: false,
          clarificationQuestion: null,
        } satisfies PlanResponse);
        return;
      }

      // ---- arrive_by -> deterministic goal plan ----
      const nowEpochMs = Date.parse(currentDateTime);
      const arrivalMs = arrivalEpochMs(extraction.targetDate, extraction.targetTime, offset);
      if (arrivalMs === null || Number.isNaN(nowEpochMs)) {
        res.status(502).json({ error: "invalid_time_context" });
        return;
      }

      // Resolve commute in priority order (saved pref -> deterministic parse of
      // the answer -> model extraction), every source range-checked 0..480. If
      // none yields a value, escalate to the explicit minutes prompt rather than
      // re-asking the same generic commute question.
      const resolution = resolveCommute(
        preferences.commuteMinutes,
        activeClarification,
        extraction.commuteMinutes
      );
      if (resolution.commuteMinutes === null) {
        const code = resolution.escalationCode ?? "commute_required";
        res.json({
          status: "clarification_required",
          clarificationCode: code,
          clarificationQuestion: clarificationQuestion(code, locale),
          needsClarification: true,
        } satisfies PlanResponse);
        return;
      }
      const commute = resolution.commuteMinutes;

      const preparation = extraction.preparationMinutes ?? preferences.preparationMinutes;
      const sleep = clampInt(extraction.requestedSleepMinutes ?? preferences.targetSleepMinutes, preferences.targetSleepMinutes, SLEEP_MIN, SLEEP_MAX);

      const result = plan({
        arrivalEpochMs: arrivalMs,
        nowEpochMs,
        offsetMinutes: offset,
        commuteMinutes: commute,
        preparationMinutes: preparation,
        sleepMinutes: sleep,
        bufferMinutes: preferences.bufferMinutes,
        preAlarmEnabled: preferences.preAlarmEnabled,
        preAlarmMinutes: preferences.preAlarmMinutes,
        backupAlarmEnabled: preferences.backupAlarmEnabled,
        backupAlarmMinutes: preferences.backupAlarmMinutes,
        destinationLabel: extraction.destinationLabel ?? "Wake up",
      });

      const alarms = result.alarms.slice(0, MAX_PLAN_ALARMS).map((a) => ({ time: a.time, date: a.date, label: a.label, role: a.role, enabled: a.enabled }));

      res.json({
        status: "success",
        kind: "goal_plan",
        plan: {
          destinationLabel: extraction.destinationLabel ?? "Wake up",
          targetTime: extraction.targetTime,
          sleepStartTime: result.sleepStartTime,
          wakeTime: result.wakeTime,
          leaveByTime: result.leaveByTime,
          sleepShortfallMinutes: result.sleepShortfallMinutes,
          assumptions: result.assumptions,
          alarms,
        },
        needsClarification: false,
        clarificationQuestion: null,
      } satisfies PlanResponse);
    } catch (err) {
      // No full user text is ever logged.
      logger.error("interpretAlarmRequest failed", err);
      res.status(502).json({ error: "provider_unavailable" });
    }
  }
);

/** Parses the fixed UTC offset (minutes) from an ISO-8601 OffsetDateTime string. */
function offsetMinutes(iso: string): number {
  // "2026-08-25T06:00:00+03:00" | "...Z"
  const m = /([+-])(\d{2}):(\d{2})$/.exec(iso.trim());
  if (!m) return 0;
  const sign = m[1] === "-" ? -1 : 1;
  return sign * (Number(m[2]) * 60 + Number(m[3]));
}
