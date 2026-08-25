/**
 * PromptHaven AI proxy — `interpretAlarmRequest`
 *
 * The ONLY place an AI provider key exists is server-side secret management
 * (Firebase Secret Manager / env). The Android app sends a public HTTPS request
 * with the user's natural-language text and receives back a validated alarm
 * prefill. This backend NEVER schedules an alarm — it only returns values that
 * the app may choose to prefill into its editor (the app's SAVE button is the
 * sole thing that ever schedules).
 *
 * Security posture:
 *   - Provider key via secret, never baked into the function bundle.
 *   - Prompt length capped (~500 chars). No full prompts are logged.
 *   - Response is strictly schema-checked and range-checked before returning.
 *   - Clarification requests returned instead of guessed answers when ambiguous.
 */
import * as functions from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";

// Fire-2 (DeepSeek) key held only in secret manager. Overridable for testing.
const PROVIDER_API_KEY = defineSecret("PROVIDER_API_KEY");
const PROVIDER_ENDPOINT = process.env.PROVIDER_ENDPOINT ?? "https://api.deepseek.com/chat/completions";

const MAX_PROMPT_LENGTH = 500;
const DAY_NAMES = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;

type DayName = (typeof DAY_NAMES)[number];

interface AlarmResult {
  status: "ok";
  alarm: { hour: number; minute: number; repeatDays: DayName[] };
}
interface ClarificationResult {
  status: "clarification";
  clarification: string;
}
type InterpretResponse = AlarmResult | ClarificationResult;

const SYSTEM_PROMPT = [
  "You are PromptHaven Alarm's configuration assistant.",
  "Convert the user's natural-language alarm request into structured JSON ONLY.",
  'Reply with exactly one JSON object: {"hour":0-23,"minute":0-59,"repeatDays":["MONDAY",...] or []}',
  "Use 24-hour time. repeatDays uses DayOfWeek enum names; [] means one-time.",
  'If the request is ambiguous about the time or days, reply instead: {"clarification":"one short question to the user"}',
  "Do not invent alarms. Do not include anything except this JSON.",
].join("\n");

export const interpretAlarmRequest = functions.onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 20,
    secrets: [PROVIDER_API_KEY],
    cors: true,
  },
  async (req, res) => {
    try {
      // Only accept small POST bodies.
      if (req.method !== "POST") {
        res.status(405).json({ error: "method_not_allowed" });
        return;
      }
      const rawPrompt = typeof req.body?.prompt === "string" ? req.body.prompt : "";
      const prompt = rawPrompt.slice(0, MAX_PROMPT_LENGTH);
      if (prompt.trim().length === 0) {
        res.status(400).json({ error: "empty_prompt" });
        return;
      }

      const providerResult = await callProvider(prompt, PROVIDER_API_KEY.value());
      // Validate/sanitize provider output — never trust raw AI JSON.
      const parsed = sanitize(providerResult);
      if (parsed === null) {
        res.status(502).json({ error: "invalid_provider_response" });
        return;
      }
      res.json(parsed);
    } catch (err) {
      // No full prompt is ever logged.
      functions.logger.error("interpretAlarmRequest failed", err);
      res.status(502).json({ error: "provider_unavailable" });
    }
  }
);

async function callProvider(prompt: string, apiKey: string): Promise<string> {
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
          { role: "system", content: SYSTEM_PROMPT },
          { role: "user", content: prompt },
        ],
        max_tokens: 150,
      }),
    });
    if (!response.ok) throw new Error(`provider status ${response.status}`);
    const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
    return data.choices?.[0]?.message?.content ?? "";
  } finally {
    clearTimeout(timer);
  }
}

/** Range-checks and whitelists every field of the provider JSON. */
function sanitize(raw: string): InterpretResponse | null {
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }

  if (typeof obj.clarification === "string" && obj.clarification.trim()) {
    return { status: "clarification", clarification: obj.clarification.trim().slice(0, 200) };
  }

  const hour = obj.hour;
  const minute = obj.minute;
  if (typeof hour !== "number" || typeof minute !== "number") return null;
  if (!Number.isInteger(hour) || hour < 0 || hour > 23) return null;
  if (!Number.isInteger(minute) || minute < 0 || minute > 59) return null;

  let repeatDays: DayName[] = [];
  if (Array.isArray(obj.repeatDays)) {
    repeatDays = obj.repeatDays.filter(
      (d): d is DayName => typeof d === "string" && (DAY_NAMES as readonly string[]).includes(d)
    );
  }

  return { status: "ok", alarm: { hour, minute, repeatDays } };
}
