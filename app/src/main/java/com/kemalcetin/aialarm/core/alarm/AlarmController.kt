package com.kemalcetin.aialarm.core.alarm

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import com.kemalcetin.aialarm.feature.assistant.engine.AiAlarmEngine
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Orchestrates the ringing lifecycle: trigger, dismiss, and snooze.
 *
 * Runs on an application-scoped coroutine so actions survive Activity/Service
 * teardown (e.g. the ringing Activity finishing immediately after a button tap).
 */
class AlarmController(
    private val context: Context,
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val notificationManager: AlarmNotificationManager,
    private val aiEngine: AiAlarmEngine,
    private val scope: CoroutineScope
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Ring-start timestamps per alarm, used to classify dismiss intent by latency. */
    private val ringStartMs = mutableMapOf<Long, Long>()

    fun handleAlarmTriggered(alarmId: Long, isSnooze: Boolean, onFinished: () -> Unit) {
        scope.launch {
            try {
                val alarm = repository.getAlarm(alarmId)
                if (alarm == null || !alarm.enabled) {
                    stopRinging()
                    return@launch
                }

                startRinging(alarm)
                ringStartMs[alarmId] = System.currentTimeMillis()
                aiEngine.onAlarmFired(alarmId, isSnooze)

                if (!isSnooze) {
                    rescheduleAfterTrigger(alarm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleAlarmTriggered failed", e)
            } finally {
                onFinished()
            }
        }
    }

    fun dismiss(alarmId: Long, onFinished: () -> Unit) {
        scope.launch {
            try {
                val latency = ringStartMs.remove(alarmId)
                    ?.let { System.currentTimeMillis() - it }
                aiEngine.onDismiss(alarmId, latency)
                stopRinging()
            } finally {
                onFinished()
            }
        }
    }

    fun snooze(alarmId: Long, onFinished: () -> Unit) {
        scope.launch {
            try {
                val alarm = repository.getAlarm(alarmId)
                stopRinging()
                if (alarm != null) {
                    aiEngine.onAlarmFired(alarmId, isSnooze = true)
                    scheduleSnooze(alarm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "snooze failed", e)
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun rescheduleAfterTrigger(alarm: Alarm) {
        if (alarm.repeatDays.isNotEmpty()) {
            scheduler.schedule(alarm)
        } else {
            repository.setEnabled(alarm.id, false)
        }
    }

    private fun scheduleSnooze(alarm: Alarm) {
        val snoozeAt = ZonedDateTime.now().plusMinutes(alarm.snoozeMinutes.toLong())
        val pendingIntent = AlarmIntentFactory.snoozeStartPendingIntent(context, alarm.id)
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    snoozeAt.toInstant().toEpochMilli(),
                    AlarmIntentFactory.fullScreenPendingIntent(context, alarm.id)
                ),
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule snooze: exact alarm permission missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze", e)
        }
    }

    private fun startRinging(alarm: Alarm) {
        val serviceIntent = AlarmIntentFactory.ringingServiceIntent(context, alarm)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun stopRinging() {
        context.stopService(AlarmIntentFactory.stopRingingIntent(context))
        notificationManager.cancel()
    }

    private companion object {
        const val TAG = "AlarmController"
    }
}
