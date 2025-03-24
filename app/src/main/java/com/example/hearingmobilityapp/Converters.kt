package com.example.hearingmobilityapp

import androidx.room.TypeConverter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Converters {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    @TypeConverter
    fun fromTimestamp(value: String?): LocalTime? {
        return value?.let {
            try {
                LocalTime.parse(it, timeFormatter)
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun timeToString(time: LocalTime?): String? {
        return time?.format(timeFormatter)
    }
}
