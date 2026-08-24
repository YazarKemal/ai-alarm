package com.kemalcetin.aialarm.domain.repository

import com.kemalcetin.aialarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    fun observeAlarm(id: Long): Flow<Alarm?>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun getEnabledAlarms(): List<Alarm>
    suspend fun insert(alarm: Alarm): Long
    suspend fun update(alarm: Alarm)
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
