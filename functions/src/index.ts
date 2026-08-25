/**
 * PromptHaven AI proxy — `interpretAlarmRequest`
 *
 * The ONLY place an AI provider key exists is server-side secret management
 * (Firebase Secret Manager / env). The Android app sends a public HTTPS request
 * with the user's natural-language text plus non-secret context (timezone,
 * locale, current time) and receives back a validated alarm prefill. This
 * backend NEVER schedules an alarm — it only returns values that the app may
 * choose to prefill into its editor (the app's SAVE button is the sole thing
 * that ever schedules).
 *
 * Security posture:
 *   - Provider key via secret, never baked into the function bundle.
 *   - Text length capped (~500 chars). No full prompts are logged.
 *   - Response is strictly schema-checked and range-checked before returning.
 *   - Clarification requests returned instead of guessed answers when ambiguous.
 *   - Firebase App Check: the request's App Check token is verified before the
 *     provider is called. Enforcement is toggled by APP_CHECK_ENFORCED so local
 *     / emulator development stays practical (see README).
 *   - Rate limiting: a lightweight in-memory per-IP token bucket throttles
 *     abuse. Fine for a single-instance proxy; for multi-instance production
 *     prefer a distributed store or Cloud Armor.
 */
import { initializeApp } from "firebase-admin/app";
import { getAppCheck } from "firebase-admin/app-check";
import * as https from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { defineSecret } from "firebase-functions/params";

// Ensure the default Admin app exists before we call getAppCheck().
initializeApp();

// Fire-2 (DeepSeek) key held only in secret manager. Overridable for testing.
const PROVIDER_API_KEY = defineSecret("PROVIDER_API_KEY");
const PROVIDER_ENDPOINT = process.env.PROVIDER_ENDPOINT ?? "https://api.deepseek.com/chat/completions";

// App Check + rate limiting are tunable via env so local/emulator work stays practical.
const APP_CHECK_ENFORCED = process.env.APP_CHECK_ENFORCED === "true";
const RATE_LIMIT_PER_MINUTE = Number(process.env.RATE_LIMIT_PER_MINUTE ?? 60);

const APP_CHECK_HEADER = "x-firebase-app-check";
const RATE_WINDOW_MS = 60_000;

const MAX_TEXT_LENGTH = 500;
const MAX_LABEL_LENGTH = 80;
const DAY_NAMES = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;

type DayName = (typeof DAY_NAMES)[number];

interface Interpretation {
  time: string; // "HH:mm"
  date: string | null; // ISO-8601 yyyy-MM-dd, null for repeating
  repeatDays: DayName[]; // [] for one-time
  label: string;
}
type InterpretResponse =
  | { status: "success"; interpretation: Interpretation; needsClarification: false; clarificationQuestion: null }
  | { status: "clarification_required"; interpretation: null; needsClarification: true; clarificationQuestion: string };

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

/**
 * Verifies the Firebase App Check token carried in the request header.
 * When APP_CHECK_ENFORCED is false (local/emulator), requests without a token
 * are allowed through so development stays practical; production must run with
 * APP_CHECK_ENFORCED=true.
 */
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

function buildSystemPrompt(timezone: string, locale: string, currentDateTime: string): string {
  return [
    "You are PromptHaven Alarm's configuration assistant.",
    "The user's current timezone, locale, and reference timestamp are provided for context so you can resolve relative dates like 'tomorrow' or 'today'.",
    `Reference timestamp (ISO-8601): ${currentDateTime}`,
    `User timezone: ${timezone}`,
    `User locale: ${locale}`,
    "Convert the user's natural-language alarm request into structured JSON ONLY.",
    'Reply with exactly one JSON object with this shape: {"time":"HH:mm","date":"YYYY-MM-DD or null","repeatDays":["MONDAY",...] or [],"label":"short label"}',
    "Use 24-hour time. repeatDays uses DayOfWeek enum names; an empty array [] means one-time.",
    'A one-time alarm on a specific calendar day sets "date". A repeating alarm sets "repeatDays" and "date":null. Never set both.',
    'If the request is ambiguous about the time, day, or date, reply instead: {"clarificationQuestion":"one short question to the user"}',
    'Keep "label" under 80 characters and meaningful. Do not invent alarms. Do not include anything except this JSON.',
  ].join("\n");
}

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

      if (text.trim().length === 0) {
        res.status(400).json({ error: "empty_text" });
        return;
      }
      // All context fields are required for a correct relative-date resolution.
      if (timezone.trim().length === 0 || locale.trim().length === 0 || currentDateTime.trim().length === 0) {
        res.status(400).json({ error: "missing_context" });
        return;
      }

      const providerResult = await callProvider(
        text,
        buildSystemPrompt(timezone, locale, currentDateTime),
        PROVIDER_API_KEY.value()
      );
      // Validate/sanitize provider output — never trust raw AI JSON.
      const parsed = sanitize(providerResult);
      if (parsed === null) {
        res.status(502).json({ error: "invalid_provider_response" });
        return;
      }
      res.json(parsed);
    } catch (err) {
      // No full user text is ever logged.
      logger.error("interpretAlarmRequest failed", err);
      res.status(502).json({ error: "provider_unavailable" });
    }
  }
);

async function callProvider(prompt: string, systemPrompt: string, apiKey: string): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(PROVIDER_ENDPOINT, {
      method: "POST",
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: "deepseek-chat",
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: prompt },
        ],
        max_tokens: 200,
      }),
    });
    if (!response.ok) throw new Error(`provider status ${response.status}`);
    const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
    return data.choices?.[0]?.message?.content ?? "";
  } finally {
    clearTimeout(timer);
  }
}

function isValidDayName(value: unknown): value is DayName {
  return typeof value === "string" && (DAY_NAMES as readonly string[]).includes(value);
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

/** Valid ISO-8601 local date (yyyy-MM-dd) or null when absent/blank. */
function parseIsoDate(value: unknown): string | null | undefined {
  if (value === null || value === undefined) return null;
  if (typeof value !== "string") return undefined; // present but wrong type
  const s = value.trim();
  if (s.length === 0) return null;
  return /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(`${s}T00:00:00Z`)) ? s : undefined;
}

/** Range-checks and whitelists every field of the provider JSON. */
function sanitize(raw: string): InterpretResponse | null {
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }

  if (typeof obj.clarificationQuestion === "string" && obj.clarificationQuestion.trim()) {
    return {
      status: "clarification_required",
      interpretation: null,
      needsClarification: true,
      clarificationQuestion: obj.clarificationQuestion.trim().slice(0, 200),
    };
  }

  const hm = parseHm(obj.time);
  if (!hm) return null;

  const date = parseIsoDate(obj.date);
  if (date === undefined) return null;

  let repeatDays: DayName[] = [];
  if (Array.isArray(obj.repeatDays)) {
    repeatDays = obj.repeatDays.filter(isValidDayName);
  }

  // A pinned date and repeat days are mutually exclusive.
  if (date !== null && repeatDays.length > 0) return null;

  const label = typeof obj.label === "string" ? obj.label.trim().slice(0, MAX_LABEL_LENGTH) : "";
  const time = `${String(hm.hour).padStart(2, "0")}:${String(hm.minute).padStart(2, "0")}`;

  return {
    status: "success",
    interpretation: { time, date, repeatDays, label },
    needsClarification: false,
    clarificationQuestion: null,
  };
}
