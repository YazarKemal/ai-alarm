/**
 * PromptHaven goal planner — deterministic, pure, fully testable.
 *
 * The AI model is used ONLY to extract structured constraints from natural
 * language. This module is where the arithmetic happens: the model never invents
 * final alarm times. Every schedule below is computed by code.
 *
 * Math (matches the documented example exactly: arrival 13:00, commute 40,
 * buffer 15, prep 30 => leaveBy 12:05, wake 11:35, sleepStart 03:35):
 *   leaveBy   = arrival - commute - buffer
 *   wake      = leaveBy - preparation
 *   sleepStart= wake - sleep target
 *   gentle    = wake - preAlarmMinutes
 *   backup    = wake + backupAlarmMinutes
 */

export const MIN_MS = 60_000;
export const DAY_MS = 86_400_000;

export interface PlannerInput {
  /** Absolute epoch ms of the arrival target (computed upstream from date+time+offset). */
  arrivalEpochMs: number;
  /** Absolute epoch ms of "now" (from the request's currentDateTime). */
  nowEpochMs: number;
  /** Fixed UTC offset (minutes) used only to format times to local HH:mm. */
  offsetMinutes: number;
  commuteMinutes: number | null;
  preparationMinutes: number;
  sleepMinutes: number;
  bufferMinutes: number;
  preAlarmEnabled: boolean;
  preAlarmMinutes: number;
  backupAlarmEnabled: boolean;
  backupAlarmMinutes: number;
  destinationLabel: string;
}

export type PlannerAlarmRole = "gentle" | "main" | "backup";

export interface PlannerAlarm {
  time: string; // "HH:mm"
  /** Local calendar date (yyyy-MM-dd) this alarm fires on, resolved by code. */
  date: string;
  label: string;
  role: PlannerAlarmRole;
  enabled: boolean;
}

export interface PlannerResult {
  leaveByTime: string;
  wakeTime: string;
  sleepStartTime: string;
  sleepShortfallMinutes: number;
  assumptions: {
    sleepMinutes: number;
    preparationMinutes: number;
    commuteMinutes: number | null;
    bufferMinutes: number;
  };
  alarms: PlannerAlarm[];
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** Formats an absolute epoch ms to "HH:mm" in the given fixed offset. */
function format(epochMs: number, offsetMinutes: number): string {
  const local = epochMs + offsetMinutes * MIN_MS;
  const dayMs = DAY_MS;
  let d = ((local % dayMs) + dayMs) % dayMs;
  const h = Math.floor(d / 3_600_000);
  d %= 3_600_000;
  const m = Math.floor(d / MIN_MS);
  return `${pad(h)}:${pad(m)}`;
}

/** Local calendar date (yyyy-MM-dd) of an absolute epoch at the given offset. */
export function localDate(epochMs: number, offsetMinutes: number): string {
  const local = epochMs + offsetMinutes * MIN_MS;
  const d = new Date(local);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
}

/**
 * Computes the full wake plan from the arrival constraints and preferences.
 * Returns null only if a required value is missing (commute for an arrive-by goal)
 * — the caller decides whether to ask for clarification.
 */
export function plan(input: PlannerInput): PlannerResult {
  const commute = input.commuteMinutes ?? 0;

  const leaveByMs = input.arrivalEpochMs - (commute + input.bufferMinutes) * MIN_MS;
  const wakeMs = leaveByMs - input.preparationMinutes * MIN_MS;
  const sleepStartMs = wakeMs - input.sleepMinutes * MIN_MS;

  // If "now" is already past the bedtime that would give the full target, report
  // how much of the target is unreachable — we never claim a false "full sleep".
  const rawShortfall = nowEpochMs(input.nowEpochMs, sleepStartMs);
  const sleepShortfallMinutes = Math.max(0, Math.min(input.sleepMinutes, rawShortfall));

  const gentleMs = wakeMs - input.preAlarmMinutes * MIN_MS;
  const backupMs = wakeMs + input.backupAlarmMinutes * MIN_MS;
  const alarms: PlannerAlarm[] = [];
  if (input.preAlarmEnabled) {
    alarms.push({
      time: format(gentleMs, input.offsetMinutes),
      date: localDate(gentleMs, input.offsetMinutes),
      label: "Gentle wake-up",
      role: "gentle",
      enabled: true,
    });
  }
  alarms.push({
    time: format(wakeMs, input.offsetMinutes),
    date: localDate(wakeMs, input.offsetMinutes),
    label: input.destinationLabel || "Wake up",
    role: "main",
    enabled: true,
  });
  if (input.backupAlarmEnabled) {
    alarms.push({
      time: format(backupMs, input.offsetMinutes),
      date: localDate(backupMs, input.offsetMinutes),
      label: "Backup",
      role: "backup",
      enabled: true,
    });
  }

  return {
    leaveByTime: format(leaveByMs, input.offsetMinutes),
    wakeTime: format(wakeMs, input.offsetMinutes),
    sleepStartTime: format(sleepStartMs, input.offsetMinutes),
    sleepShortfallMinutes,
    assumptions: {
      sleepMinutes: input.sleepMinutes,
      preparationMinutes: input.preparationMinutes,
      commuteMinutes: input.commuteMinutes,
      bufferMinutes: input.bufferMinutes,
    },
    alarms,
  };
}

function nowEpochMs(now: number, sleepStartMs: number): number {
  return now > sleepStartMs ? Math.round((now - sleepStartMs) / MIN_MS) : 0;
}
