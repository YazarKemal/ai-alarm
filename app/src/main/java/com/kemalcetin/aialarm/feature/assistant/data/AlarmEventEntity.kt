package com.kemalcetin.aialarm.feature.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEventType
import com.kemalcetin.aialarm.feature.assistant.model.DismissIntent
import java.time.Instant

@Entity(tableName = "alarm_events")
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alarmId: Long,
    val type: String,
    val occurredAt: Long,
    val dismissLatencyMs: Long? = null,
    val snoozeCount: Int = 0,
    val dismissIntent: String? = null
)

fun AlarmEvent.toEntity() = AlarmEventEntity(
    alarmId = alarmId,
    type = type.name,
    occurredAt = occurredAt.toEpochMilli(),
    dismissLatencyMs = dismissLatencyMs,
    snoozeCount = snoozeCount,
    dismissIntent = dismissIntent?.name
)

fun AlarmEventEntity.toDomain() = AlarmEvent(
    alarmId = alarmId,
    type = AlarmEventType.valueOf(type),
    occurredAt = Instant.ofEpochMilli(occurredAt),
    dismissLatencyMs = dismissLatencyMs,
    snoozeCount = snoozeCount,
    dismissIntent = dismissIntent?.let { DismissIntent.valueOf(it) }
)
