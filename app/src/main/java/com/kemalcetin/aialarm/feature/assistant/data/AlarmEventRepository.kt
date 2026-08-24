package com.kemalcetin.aialarm.feature.assistant.data

import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AlarmEventRepository {
    suspend fun record(event: AlarmEvent)
    suspend fun recentEvents(limit: Int): List<AlarmEvent>
    suspend fun eventsForAlarm(alarmId: Long): List<AlarmEvent>
    fun observeRecent(limit: Int): Flow<List<AlarmEvent>>
}

class AlarmEventRepositoryImpl(private val dao: AlarmEventDao) : AlarmEventRepository {

    override suspend fun record(event: AlarmEvent) {
        dao.insert(event.toEntity())
    }

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> =
        dao.getRecent(limit).map { it.toDomain() }

    override suspend fun eventsForAlarm(alarmId: Long): List<AlarmEvent> =
        dao.eventsForAlarm(alarmId).map { it.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<AlarmEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
}
