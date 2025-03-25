package com.example.hearingmobilityapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalendarDateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarDates(calendarDates: List<CalendarDateEntity>)

    @Query("SELECT * FROM calendar_dates")
    suspend fun getAllCalendarDates(): List<CalendarDateEntity>

    @Query("SELECT * FROM calendar_dates WHERE service_id = :serviceId")
    suspend fun getCalendarDatesByServiceId(serviceId: String): List<CalendarDateEntity>
}
