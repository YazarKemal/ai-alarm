"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeLocale,
  parseDurationToMinutes,
  validateCommute,
  parseClarifications,
  buildProviderTurns,
  resolveCommute,
  clarificationQuestion,
} = require("../lib/logic.js");

// ---------------------------------------------------------------------------
// BCP-47 locale normalization
// ---------------------------------------------------------------------------

test("1. tr-TR normalizes to tr", () => {
  assert.equal(normalizeLocale("tr-TR"), "tr");
  assert.equal(normalizeLocale("tr"), "tr");
});

test("2. en-US normalizes to en", () => {
  assert.equal(normalizeLocale("en-US"), "en");
  assert.equal(normalizeLocale("en-GB"), "en");
});

test("3. pt-BR remains pt-BR", () => {
  assert.equal(normalizeLocale("pt-BR"), "pt-BR");
  assert.equal(normalizeLocale("pt-br"), "pt-BR");
});

test("4. regional variants for all supported languages", () => {
  assert.equal(normalizeLocale("es-ES"), "es");
  assert.equal(normalizeLocale("de-DE"), "de");
  assert.equal(normalizeLocale("fr-FR"), "fr");
  assert.equal(normalizeLocale("it-IT"), "it");
  assert.equal(normalizeLocale("id-ID"), "id");
  assert.equal(normalizeLocale("hi-IN"), "hi");
  assert.equal(normalizeLocale("ja-JP"), "ja");
  assert.equal(normalizeLocale("ko-KR"), "ko");
  assert.equal(normalizeLocale("ar-SA"), "ar");
});

test("locale normalization stops the English fallback for tr-TR", () => {
  // Regression for the real-device bug: Turkish app language must stay Turkish.
  const q = clarificationQuestion("commute_required", "tr-TR");
  assert.equal(q, "Oraya ulaşman genellikle kaç dakika sürüyor?");
});

// ---------------------------------------------------------------------------
// Deterministic duration interpretation
// ---------------------------------------------------------------------------

test("6. Turkish '1 saat kadar' -> commuteMinutes 60", () => {
  assert.equal(parseDurationToMinutes("1 saat kadar"), 60);
  assert.equal(parseDurationToMinutes("1 saat"), 60);
  assert.equal(parseDurationToMinutes("60 dakika"), 60);
  assert.equal(parseDurationToMinutes("yaklaşık 45 dakika"), 45);
});

test("7. Turkish '1 buçuk saat' -> 90", () => {
  assert.equal(parseDurationToMinutes("1 buçuk saat"), 90);
});

test("8. English 'about an hour' -> 60", () => {
  assert.equal(parseDurationToMinutes("about an hour"), 60);
  assert.equal(parseDurationToMinutes("45 minutes"), 45);
  assert.equal(parseDurationToMinutes("an hour and a half"), 90);
});

test("duration that cannot be interpreted returns null", () => {
  assert.equal(parseDurationToMinutes(""), null);
  assert.equal(parseDurationToMinutes("bilmiyorum"), null);
});

test("commute validation clamps/accepts only 0..480", () => {
  assert.equal(validateCommute(60), 60);
  assert.equal(validateCommute(480), 480);
  assert.equal(validateCommute(0), 0);
  assert.equal(validateCommute(481), null);
  assert.equal(validateCommute(-5), null);
  assert.equal(validateCommute(null), null);
  assert.equal(validateCommute(60.4), 60);
});

// ---------------------------------------------------------------------------
// Structured clarifications
// ---------------------------------------------------------------------------

test("parseClarifications keeps known codes with answers, drops junk", () => {
  const out = parseClarifications([
    { code: "commute_required", question: "q?", answer: "1 saat kadar" },
    { code: "unknown_code", question: "q", answer: "x" }, // unknown -> dropped
    { code: "commute_minutes_required", question: "q", answer: "" }, // empty -> dropped
    null, // junk -> dropped
  ]);
  assert.equal(out.length, 1);
  assert.equal(out[0].code, "commute_required");
  assert.equal(out[0].answer, "1 saat kadar");
});

// ---------------------------------------------------------------------------
// Message ordering
// ---------------------------------------------------------------------------

test("9. provider turns follow SYSTEM, USER original, ASSISTANT q, USER answer", () => {
  const original = "Ben erken kalkmayı sevmiyorum ama öğlen 1 gibi okulda olmam lazım";
  const turns = buildProviderTurns(original, [
    { code: "commute_required", question: "Oraya ulaşman genellikle kaç dakika sürüyor?", answer: "1 saat kadar" },
  ]);
  assert.equal(turns.length, 3);
  assert.deepEqual(turns[0], { role: "user", content: original });
  assert.deepEqual(turns[1], { role: "assistant", content: "Oraya ulaşman genellikle kaç dakika sürüyor?" });
  assert.deepEqual(turns[2], { role: "user", content: "1 saat kadar" });
});

test("message order never puts the original request after a clarification answer", () => {
  const original = "original request";
  const turns = buildProviderTurns(original, [
    { code: "commute_required", question: "q?", answer: "a1" },
    { code: "commute_minutes_required", question: "q2", answer: "a2" },
  ]);
  assert.equal(turns[0].content, original);
  assert.equal(turns[0].role, "user");
  const contents = turns.map((t) => t.content);
  assert.deepEqual(contents, [original, "q?", "a1", "q2", "a2"]);
});

// ---------------------------------------------------------------------------
// Commute resolution -> GOAL_PLAN (not re-clarification) + anti-loop
// ---------------------------------------------------------------------------

test("5. exact Turkish real-device scenario resolves commute to 60", () => {
  // Original request + commute_required answer "1 saat kadar".
  const res = resolveCommute(null, { code: "commute_required", question: "q", answer: "1 saat kadar" }, null);
  assert.equal(res.commuteMinutes, 60);
  assert.equal(res.escalationCode, null);
});

test("10. valid clarification produces GOAL_PLAN, not the same clarification", () => {
  const res = resolveCommute(null, { code: "commute_required", question: "q", answer: "1 saat" }, 60);
  assert.equal(res.commuteMinutes, 60);
  assert.equal(res.escalationCode, null); // planning proceeds immediately
});

test("model extraction is accepted and range-checked", () => {
  const res = resolveCommute(null, null, 90);
  assert.equal(res.commuteMinutes, 90);
  assert.equal(res.escalationCode, null);
});

test("saved preference takes priority", () => {
  const res = resolveCommute(35, { code: "commute_required", question: "q", answer: "1 saat" }, 90);
  assert.equal(res.commuteMinutes, 35);
  assert.equal(res.escalationCode, null);
});

test("11. anti-loop escalation after a repeated invalid answer", () => {
  // First attempt: generic commute_required answer is unparseable -> escalate to
  // the explicit minutes prompt. It must never go back to commute_required.
  const first = resolveCommute(null, { code: "commute_required", question: "q", answer: "bilmiyorum" }, null);
  assert.equal(first.commuteMinutes, null);
  assert.equal(first.escalationCode, "commute_minutes_required");

  // Second attempt (already escalated): still unparseable -> stays explicit.
  const second = resolveCommute(null, { code: "commute_minutes_required", question: "q", answer: "hmm" }, null);
  assert.equal(second.commuteMinutes, null);
  assert.equal(second.escalationCode, "commute_minutes_required");
});

test("no prior answer and no model value asks the generic commute question", () => {
  const res = resolveCommute(null, null, null);
  assert.equal(res.commuteMinutes, null);
  assert.equal(res.escalationCode, "commute_required");
});
