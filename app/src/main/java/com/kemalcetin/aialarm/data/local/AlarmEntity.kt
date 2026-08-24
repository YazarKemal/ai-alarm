package com.kemalcetin.aialarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kemalcetin.aialarm.domain.model.Alarm
import java.time.DayOfWeek
import java.time.Instant

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    val repeatDays: Set<DayOfWeek>,
    val vibrate: Boolean,
    val soundUri: String?,
    val snoozeMinutes: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    enabled = enabled,
    repeatDays = repeatDays,
    vibrate = vibrate,
    soundUri = soundUri,
    snoozeMinutes = snoozeMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    enabled = enabled,
    repeatDays = repeatDays,
    vibrate = vibrate,
    soundUri = soundUri,
    snoozeMinutes = snoozeMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt
)
