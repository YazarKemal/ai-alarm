package com.kemalcetin.aialarm.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {

    private lateinit var db: AiAlarmDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AiAlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.alarmDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadBack() = runBlocking {
        val entity = AlarmEntity(
            id = 0L,
            hour = 7,
            minute = 30,
            label = "Morning",
            enabled = true,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            vibrate = true,
            soundUri = null,
            snoozeMinutes = 5,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        val id = dao.insert(entity)
        assertTrue(id > 0)

        val fetched = dao.getById(id)
        assertEquals(7, fetched?.hour)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), fetched?.repeatDays)
    }

    @Test
    fun setEnabledUpdatesRow() = runBlocking {
        val entity = AlarmEntity(
            id = 0L, hour = 6, minute = 0, label = "Early", enabled = true,
            repeatDays = emptySet(), vibrate = false, soundUri = null,
            snoozeMinutes = 10, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val id = dao.insert(entity)

        dao.setEnabled(id, false)

        assertEquals(false, dao.getById(id)?.enabled)
    }
}
