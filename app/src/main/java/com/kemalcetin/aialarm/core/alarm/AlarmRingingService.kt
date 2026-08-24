package com.kemalcetin.aialarm.core.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kemalcetin.aialarm.R

/**
 * Foreground service active only while an alarm is ringing. Plays the system
 * default alarm sound and vibrates until the alarm is dismissed or snoozed.
 */
class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Long = -1L
    private var label: String = ""
    private var shouldVibrate: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AlarmIntentFactory.ACTION_STOP_RINGING) {
            stopSelf()
            return START_NOT_STICKY
        }

        alarmId = intent?.getLongExtra(AlarmIntentFactory.EXTRA_ALARM_ID, -1L) ?: -1L
        label = intent?.getStringExtra(AlarmIntentFactory.EXTRA_ALARM_LABEL) ?: ""
        shouldVibrate = intent?.getBooleanExtra(AlarmIntentFactory.EXTRA_ALARM_VIBRATE, false) ?: false

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startSound()
        startVibration()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSound()
        stopVibration()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildForegroundNotification() =
        notificationManager().buildRingingNotification(alarmId, label)

    private fun notificationManager() =
        (application as com.kemalcetin.aialarm.AiAlarmApplication).container.notificationManager

    private fun startSound() {
        val uri = resolveAlarmSoundUri() ?: return
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    private fun resolveAlarmSoundUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun startVibration() {
        if (!shouldVibrate) return
        vibrator = systemVibrator() ?: return
        val pattern = longArrayOf(0L, 600L, 600L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun systemVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun stopSound() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: IllegalStateException) {
            }
            player.release()
        }
        mediaPlayer = null
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
