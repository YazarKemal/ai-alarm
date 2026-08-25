"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { plan, MIN_MS } = require("../lib/planner.js");

// Fixed, deterministic inputs. Offset 0 keeps the arithmetic trivially visible.
// Arrival: 2026-08-26 13:00 local (offset 0) => epoch = Date.UTC(2026,7,26,13,0).
const ARRIVAL_MS = Date.UTC(2026, 7, 26, 13, 0, 0);

function base(overrides = {}) {
  return {
    arrivalEpochMs: ARRIVAL_MS,
    nowEpochMs: ARRIVAL_MS - 12 * 3600 * 1000, // 12h before, plenty of time
    offsetMinutes: 0,
    commuteMinutes: 40,
    preparationMinutes: 30,
    sleepMinutes: 480,
    bufferMinutes: 15,
    preAlarmEnabled: true,
    preAlarmMinutes: 10,
    backupAlarmEnabled: true,
    backupAlarmMinutes: 10,
    destinationLabel: "School",
    ...overrides,
  };
}

test("spec example: 13:00 arrival, commute 40, prep 30, sleep 8h", () => {
  const r = plan(base());
  assert.equal(r.leaveByTime, "12:05"); // 13:00 - 40m
  assert.equal(r.wakeTime, "11:35"); // 12:05 - 30m
  assert.equal(r.sleepStartTime, "03:35"); // 11:35 - 8h
  assert.equal(r.sleepShortfallMinutes, 0);
});

test("alarms: gentle at wake-10, main at wake, backup at wake+10", () => {
  const r = plan(base());
  assert.equal(r.alarms.length, 3);
  assert.equal(r.alarms[0].time, "11:25");
  assert.equal(r.alarms[0].role, "gentle");
  assert.equal(r.alarms[1].time, "11:35");
  assert.equal(r.alarms[1].role, "main");
  assert.equal(r.alarms[2].time, "11:45");
  assert.equal(r.alarms[2].role, "backup");
  assert.ok(r.alarms.every((a) => a.enabled === true));
});

test("commute null leaves wake unaffected (treated as 0) and reported as null", () => {
  const r = plan(base({ commuteMinutes: null, bufferMinutes: 0 }));
  assert.equal(r.leaveByTime, "13:00");
  assert.equal(r.wakeTime, "12:30");
  assert.equal(r.assumptions.commuteMinutes, null);
});

test("disabled pre and backup alarms yields a single main alarm", () => {
  const r = plan(base({ preAlarmEnabled: false, backupAlarmEnabled: false }));
  assert.equal(r.alarms.length, 1);
  assert.equal(r.alarms[0].role, "main");
});

test("sleep shortfall when now is past the bedtime", () => {
  // now = 04:00 on the target date; bedtime was 03:35 -> lost 25 min.
  const r = plan(base({ nowEpochMs: Date.UTC(2026, 7, 26, 4, 0, 0) }));
  assert.equal(r.sleepShortfallMinutes, 25);
});

test("shortfall is capped at the full sleep target", () => {
  // now long after wake -> would be huge, but capped at sleepMinutes (480).
  const r = plan(base({ nowEpochMs: Date.UTC(2026, 7, 26, 20, 0, 0) }));
  assert.equal(r.sleepShortfallMinutes, 480);
});

test("zero shortfall when now is before bedtime", () => {
  const r = plan(base({ nowEpochMs: Date.UTC(2026, 7, 26, 2, 0, 0) }));
  assert.equal(r.sleepShortfallMinutes, 0);
});

test("custom sleep target shifts the sleep start time", () => {
  const r = plan(base({ sleepMinutes: 360 }));
  assert.equal(r.sleepStartTime, "05:35"); // 11:35 - 6h
  assert.equal(r.assumptions.sleepMinutes, 360);
});

test("time arithmetic handles pre-alarm crossing into the previous day", () => {
  // buffer 0 isolates commute/prep. Arrival 00:45, commute 10 -> leave 00:35,
  // prep 30 -> wake 00:05, gentle 10m before -> 23:55 (wraps previous day).
  const arr = Date.UTC(2026, 7, 26, 0, 45, 0);
  const r = plan(base({ arrivalEpochMs: arr, commuteMinutes: 10, preparationMinutes: 30, bufferMinutes: 0 }));
  assert.equal(r.wakeTime, "00:05");
  assert.equal(r.alarms[0].time, "23:55");
  assert.equal(r.alarms[0].role, "gentle");
});
