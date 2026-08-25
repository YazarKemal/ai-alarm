/**
 * Pure, framework-free PromptHaven AI decision logic. No Firebase imports here,
 * so these functions are trivially unit-testable without any cloud dependencies.
 * The endpoint (index.ts) imports and re-exports them.
 */

export const MAX_TEXT_LENGTH = 500;
export const MAX_CLARIFICATIONS = 6;

export const COMMUTE_MIN = 0;
export const COMMUTE_MAX = 480;

export type ClarificationCode = "commute_required" | "commute_minutes_required";

export const KNOWN_CLARIFICATION_CODES = new Set<string>(["commute_required", "commute_minutes_required"]);

// ---------------------------------------------------------------------------
// Locale normalization (BCP-47) + localized questions
// ---------------------------------------------------------------------------

/**
 * Normalizes a BCP-47 tag to the canonical key used by the question maps.
 * Regional variants collapse to their primary subtag; Brazilian Portuguese is
 * the one supported pt variant and stays "pt-BR".
 *   tr-TR -> tr, tr -> tr, en-US -> en, es-ES -> es, pt-BR/pt-br/pt -> pt-BR
 */
export function normalizeLocale(locale: string): string {
  const lower = locale.trim().toLowerCase();
  if (!lower) return "";
  const primary = lower.split("-")[0];
  return primary === "pt" ? "pt-BR" : primary;
}

const COMMUTE_QUESTIONS: Record<string, string> = {
  en: "How long does it usually take you to get there?",
  tr: "Oraya ulaşman genellikle kaç dakika sürüyor?",
  es: "¿Cuánto tiempo sueles tardar en llegar?",
  "pt-BR": "Quanto tempo você normalmente leva para chegar lá?",
  de: "Wie lange brauchst du normalerweise, um dorthin zu kommen?",
  fr: "Combien de temps vous faut-il habituellement pour y arriver ?",
  it: "Quanto tempo impieghi di solito ad arrivare?",
  id: "Berapa lama biasanya kamu sampai ke sana?",
  hi: "आपको वहाँ पहुँचने में आमतौर पर कितना समय लगता है?",
  ja: "そこまで通常どれくらいかかりますか？",
  ko: "거기까지 보통 얼마나 걸리나요?",
  ar: "كم يستغرق وصولك إلى هناك عادة؟",
};

/** Explicit escalation: ask for the travel time in plain minutes. */
const COMMUTE_MINUTES_QUESTIONS: Record<string, string> = {
  en: "Please enter the travel time in minutes, for example: 60 minutes.",
  tr: "Yolculuk süresini dakika olarak yazar mısın? Örneğin: 60 dakika.",
  es: "¿Puedes escribir el tiempo de viaje en minutos? Por ejemplo: 60 minutos.",
  "pt-BR": "Você pode escrever o tempo de viagem em minutos? Por exemplo: 60 minutos.",
  de: "Kannst du die Fahrzeit in Minuten angeben? Zum Beispiel: 60 Minuten.",
  fr: "Peux-tu écrire le temps de trajet en minutes ? Par exemple : 60 minutes.",
  it: "Puoi scrivere il tempo di viaggio in minuti? Per esempio: 60 minuti.",
  id: "Bisakah kamu menuliskan waktu perjalanan dalam menit? Contoh: 60 menit.",
  hi: "क्या आप यात्रा का समय मिनटों में लिख सकते हैं? उदाहरण के लिए: 60 मिनट।",
  ja: "移動時間を分で入力してもらえますか？例：60分。",
  ko: "이동 시간을 분 단위로 입력해 주시겠어요? 예: 60분.",
  ar: "هل يمكنك كتابة وقت السفر بالدقائق؟ على سبيل المثال: 60 دقيقة.",
};

/** A fallback question for the given map key, or the English default. */
export function clarificationQuestion(code: ClarificationCode | null, locale: string): string {
  const map = code === "commute_minutes_required" ? COMMUTE_MINUTES_QUESTIONS : COMMUTE_QUESTIONS;
  return map[normalizeLocale(locale)] ?? map.en;
}

// ---------------------------------------------------------------------------
// Deterministic duration interpretation
// ---------------------------------------------------------------------------

/**
 * Deterministically interprets a natural-language travel duration into minutes.
 * This is the safety net the planner trusts: even if the model's constrained
 * extraction returns null, these natural answers still resolve.
 *
 *   Turkish:  "1 saat kadar" -> 60, "1 saat" -> 60, "60 dakika" -> 60,
 *             "yaklaşık 45 dakika" -> 45, "1 buçuk saat" -> 90
 *   English:  "about an hour" -> 60, "45 minutes" -> 45,
 *             "an hour and a half" -> 90
 */
export function parseDurationToMinutes(raw: string): number | null {
  const s = raw.toLowerCase().trim();
  if (!s) return null;

  // "an hour and a half" / "one and a half hours" -> 90
  if (/hour and a half|one and a half hour/.test(s)) return 90;
  // "X buçuk saat" / "saat buçuk" -> (X + 0.5) hours; bare "buçuk saat" -> 90
  if (/buçuk saat|saat buçuk/.test(s)) {
    const n = s.match(/(\d+(?:[.,]\d+)?)/);
    return Math.round((n ? parseFloat(n[1].replace(",", ".")) : 1) * 60 + 30);
  }
  // "half an hour" / "yarım saat" -> 30
  if (/half an hour|yar\\.?ım saat/.test(s)) return 30;

  // number + unit ("60 dakika", "1 saat", "45 minutes", "1.5 hours")
  const re = /(\d+(?:[.,]\d+)?)\s*(hours?|hrs?|saat|saatleri|minutes?|mins?|dakika)/g;
  let total = 0;
  let found = false;
  let m: RegExpExecArray | null;
  while ((m = re.exec(s)) !== null) {
    const num = parseFloat(m[1].replace(",", "."));
    const mult = /hour|hr|saat/.test(m[2]) ? 60 : 1;
    total += num * mult;
    found = true;
  }
  if (found) return Math.round(total);

  // word + unit ("about an hour", "one hour", "bir saat")
  const wordNumber = (w: string | undefined): number =>
    ({ a: 1, an: 1, one: 1, bir: 1, iki: 2, two: 2, üç: 3, three: 3 } as Record<string, number>)[w ?? "one"] ?? 1;
  const wordRe = s.match(/(a|an|one|bir|iki|two|üç|three)?\s*(hours?|hrs?|saat|minutes?|mins?|dakika)/);
  if (wordRe) {
    const mult = /hour|hr|saat/.test(wordRe[2]) ? 60 : 1;
    return Math.round(wordNumber(wordRe[1]) * mult);
  }
  return null;
}

/** Rounds and enforces the commute range 0..480. Never trusts raw model output. */
export function validateCommute(n: number | null): number | null {
  if (n === null) return null;
  const r = Math.round(n);
  return r >= COMMUTE_MIN && r <= COMMUTE_MAX ? r : null;
}

// ---------------------------------------------------------------------------
// Structured clarifications
// ---------------------------------------------------------------------------

export interface Clarification {
  code: string;
  question: string;
  answer: string;
}

export function parseClarifications(raw: unknown): Clarification[] {
  if (!Array.isArray(raw)) return [];
  const out: Clarification[] = [];
  for (const item of raw.slice(0, MAX_CLARIFICATIONS)) {
    const c = (item ?? {}) as Record<string, unknown>;
    const code = typeof c.code === "string" ? c.code.trim() : "";
    const question = typeof c.question === "string" ? c.question.trim() : "";
    const answer = typeof c.answer === "string" ? c.answer.trim() : "";
    if (code && answer && KNOWN_CLARIFICATION_CODES.has(code)) {
      out.push({ code, question, answer: answer.slice(0, MAX_TEXT_LENGTH) });
    }
  }
  return out;
}

export interface CommuteResolution {
  /** A usable commute in 0..480, or null if it could not be resolved. */
  commuteMinutes: number | null;
  /**
   * null when planning can proceed; otherwise the clarification code to ask.
   * A commute answer that cannot be parsed escalates to "commute_minutes_required"
   * (explicit minutes prompt) so the same generic question is never re-asked.
   */
  escalationCode: ClarificationCode | null;
}

/**
 * Resolves the commute for an arrive-by goal in priority order, every source
 * range-checked 0..480:
 *   1. saved preference,
 *   2. deterministic parse of the clarification answer,
 *   3. the model's constrained extraction (already validated).
 * Returns the escalation code when no source yields a usable value.
 */
export function resolveCommute(
  savedCommute: number | null,
  activeClarification: Clarification | null,
  modelCommute: number | null
): CommuteResolution {
  let commute = savedCommute;
  if (commute === null && activeClarification) {
    commute = validateCommute(parseDurationToMinutes(activeClarification.answer));
  }
  if (commute === null) {
    commute = validateCommute(modelCommute);
  }
  if (commute !== null) {
    return { commuteMinutes: commute, escalationCode: null };
  }
  // Could not resolve. Escalate only if we already asked (a commute answer is
  // present but unparseable); otherwise ask the generic commute question once.
  return {
    commuteMinutes: null,
    escalationCode: activeClarification ? "commute_minutes_required" : "commute_required",
  };
}

/**
 * Builds the ordered provider conversation for the extraction call.
 * Semantic order is fixed: the original request first, then one
 * ASSISTANT(question)/USER(answer) pair per structured clarification.
 *   SYSTEM, USER original request, [ASSISTANT question, USER answer] ...
 */
export function buildProviderTurns(text: string, clarifications: Clarification[]): Array<{ role: string; content: string }> {
  const turns: Array<{ role: string; content: string }> = [{ role: "user", content: text }];
  for (const c of clarifications) {
    if (c.question) turns.push({ role: "assistant", content: c.question.slice(0, MAX_TEXT_LENGTH) });
    turns.push({ role: "user", content: c.answer });
  }
  return turns;
}
