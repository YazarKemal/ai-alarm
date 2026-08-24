package com.kemalcetin.aialarm.data.repository

import com.kemalcetin.aialarm.data.local.AlarmDao
import com.kemalcetin.aialarm.data.local.toDomain
import com.kemalcetin.aialarm.data.local.toEntity
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepositoryImpl(
    private val alarmDao: AlarmDao
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        alarmDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeAlarm(id: Long): Flow<Alarm?> =
        alarmDao.observeById(id).map { it?.toDomain() }

    override suspend fun getAlarm(id: Long): Alarm? = alarmDao.getById(id)?.toDomain()

    override suspend fun getEnabledAlarms(): List<Alarm> =
        alarmDao.getEnabled().map { it.toDomain() }

    override suspend fun insert(alarm: Alarm): Long = alarmDao.insert(alarm.toEntity())

    override suspend fun update(alarm: Alarm) = alarmDao.update(alarm.toEntity())

    override suspend fun delete(id: Long) = alarmDao.delete(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) =
        alarmDao.setEnabled(id, enabled)
}
