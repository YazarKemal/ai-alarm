package com.kemalcetin.aialarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kemalcetin.aialarm.data.local.converters.Converters
import com.kemalcetin.aialarm.feature.assistant.data.AlarmEventDao
import com.kemalcetin.aialarm.feature.assistant.data.AlarmEventEntity

@Database(entities = [AlarmEntity::class, AlarmEventEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AiAlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

    abstract fun alarmEventDao(): AlarmEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS alarm_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "alarmId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "occurredAt INTEGER NOT NULL, " +
                        "dismissLatencyMs INTEGER, " +
                        "snoozeCount INTEGER NOT NULL, " +
                        "dismissIntent TEXT)"
                )
            }
        }

        // v3 adds the optional oneTimeDate column (ISO-8601 string, NULL for
        // repeating alarms / legacy one-time alarms without an explicit date).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN oneTimeDate TEXT")
            }
        }

        fun create(context: Context): AiAlarmDatabase =
            Room.databaseBuilder(
                context,
                AiAlarmDatabase::class.java,
                "ai_alarm.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
