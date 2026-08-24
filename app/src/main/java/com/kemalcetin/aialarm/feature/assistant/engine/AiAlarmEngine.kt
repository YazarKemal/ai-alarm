package com.kemalcetin.aialarm.feature.assistant.engine

import android.util.Log
import com.kemalcetin.aialarm.core.alarm.AlarmScheduler
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import com.kemalcetin.aialarm.feature.assistant.analytics.DismissIntentAnalyzer
import com.kemalcetin.aialarm.feature.assistant.data.AlarmEventRepository
import com.kemalcetin.aialarm.feature.assistant.learning.ExpectedSlot
import com.kemalcetin.aialarm.feature.assistant.learning.ScheduleLearner
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEventType
import com.kemalcetin.aialarm.feature.assistant.network.AiInsightsProvider
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The assistant brain. Records alarm interactions (for learning + dismiss-intent
 * classification) and, when enabled, proactively re-creates an alarm the user
 * likely forgot. Classification is always on-device; DeepSeek is an optional
 * enhancement for finding patterns the simple learner misses.
 */
class AiAlarmEngine(
    private val eventRepository: AlarmEventRepository,
    private val alarmRepository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val preferences: AppPreferences,
    private val insightsProvider: AiInsightsProvider?,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val learner = ScheduleLearner()
    private val snoozeCounts = mutableMapOf<Long, Int>()

    fun onAlarmFired(alarmId: Long, isSnooze: Boolean): Job = scope.launch {
        try {
            if (isSnooze) {
                snoozeCounts[alarmId] = (snoozeCounts[alarmId] ?: 0) + 1
                record(AlarmEvent(alarmId, AlarmEventType.SNOOZED, Instant.now(clock)))
            } else {
                snoozeCounts[alarmId] = 0
                record(AlarmEvent(alarmId, AlarmEventType.FIRED, Instant.now(clock)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "record fired/snooze failed", e)
        }
    }

    fun onDismiss(alarmId: Long, latencyMs: Long?): Job = scope.launch {
        try {
            val count = snoozeCounts.remove(alarmId) ?: 0
            val intent = DismissIntentAnalyzer.classify(latencyMs, count)
            record(AlarmEvent(alarmId, AlarmEventType.DISMISSED, Instant.now(clock), latencyMs, count, intent))
        } catch (e: Exception) {
            Log.e(TAG, "record dismiss failed", e)
        }
    }

    /** Runs a background check; if enabled, auto-creates a forgotten alarm. */
    fun runAutoScheduleCheck(): Job = scope.launch {
        try {
            if (!preferences.autoCreateEnabled.first()) return@launch
            val enabled = alarmRepository.getEnabledAlarms()
            val from = ZonedDateTime.now(clock.withZone(ZoneId.systemDefault()))
            val slots = learner.learnSlots(eventRepository.recentEvents(50))
            val localSlot = learner.nextExpectedTrigger(slots, enabled, from)
            val target = localSlot ?: insightSlot(enabled, from)
            if (target == null || dedupedRecently()) return@launch
            createAndSchedule(target)
        } catch (e: Exception) {
            Log.e(TAG, "auto schedule check failed", e)
        }
    }

    private suspend fun record(event: AlarmEvent) {
        eventRepository.record(event)
    }

    /** Falls back to DeepSeek when the local learner found nothing. */
    private suspend fun insightSlot(enabled: List<Alarm>, from: ZonedDateTime): ExpectedSlot? {
        val provider = insightsProvider ?: return null
        val insight = withTimeoutOrNull(8_000) {
            provider.suggestForgottenAlarm(buildSummary(enabled))
        } ?: return null
        val hour = insight.suggestedHour ?: return null
        val minute = insight.suggestedMinute ?: return null
        val days = insight.suggestedDays ?: DayOfWeek.entries.toSet()
        val slot = ExpectedSlot(hour, minute, days)
        // Reuse the learner to drop suggestions already covered or outside the horizon.
        return learner.nextExpectedTrigger(listOf(slot), enabled, from)?.let { slot }
    }

    private suspend fun buildSummary(enabled: List<Alarm>): String {
        val events = eventRepository.recentEvents(50)
        return buildString {
            appendLine("Recent alarm events (type hour:minute weekday):")
            events.forEach { e ->
                val at = e.occurredAt.atZone(ZoneId.systemDefault())
                appendLine("${e.type} ${at.hour}:${at.minute} ${at.dayOfWeek}")
            }
            append("Currently enabled alarms: ")
            append(enabled.joinToString { "${it.hour}:${it.minute}" })
        }
    }

    private suspend fun dedupedRecently(): Boolean {
        val last = preferences.aiLastAutoCreateTime.first()
        if (last <= 0L) return false
        return Instant.now(clock).toEpochMilli() - last < AUTO_CREATE_DEDUPE_MS
    }

    private suspend fun createAndSchedule(slot: ExpectedSlot) {
        val now = Instant.now(clock)
        val alarm = Alarm(
            id = 0L,
            hour = slot.hour,
            minute = slot.minute,
            label = "AI Alarm",
            enabled = true,
            repeatDays = slot.repeatDays,
            vibrate = true,
            soundUri = null,
            snoozeMinutes = 5,
            createdAt = now,
            updatedAt = now
        )
        val id = alarmRepository.insert(alarm)
        scheduler.schedule(alarm.copy(id = id))
        record(AlarmEvent(id, AlarmEventType.AUTO_CREATED, now))
        preferences.setAiLastAutoCreateTime(now.toEpochMilli())
        Log.i(TAG, "Auto-created forgotten alarm ${slot.hour}:${slot.minute}")
    }

    companion object {
        private const val TAG = "AiAlarmEngine"
        private const val AUTO_CREATE_DEDUPE_MS = 6L * 60 * 60 * 1000
    }
}
