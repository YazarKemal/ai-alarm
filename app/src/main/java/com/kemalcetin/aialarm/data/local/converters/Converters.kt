package com.kemalcetin.aialarm.data.local.converters

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant

class Converters {

    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>): String =
        days.joinToString(",") { it.value.toString() }

    @TypeConverter
    fun toDayOfWeekSet(value: String): Set<DayOfWeek> =
        if (value.isBlank()) {
            emptySet()
        } else {
            value.split(',').map { DayOfWeek.of(it.toInt()) }.toSet()
        }

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
