package com.kemalcetin.aialarm.feature.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmEventDao {

    @Insert
    suspend fun insert(event: AlarmEventEntity): Long

    @Query("SELECT * FROM alarm_events ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AlarmEventEntity>

    @Query("SELECT * FROM alarm_events ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE alarmId = :alarmId ORDER BY occurredAt DESC")
    suspend fun eventsForAlarm(alarmId: Long): List<AlarmEventEntity>
}
