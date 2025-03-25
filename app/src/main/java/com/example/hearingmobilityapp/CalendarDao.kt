package com.example.hearingmobilityapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalendarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendar(calendar: List<CalendarEntity>)

    @Query("SELECT * FROM calendar")
    suspend fun getAllCalendar(): List<CalendarEntity>

    @Query("SELECT * FROM calendar WHERE service_id = :serviceId")
    suspend fun getCalendarByServiceId(serviceId: String): CalendarEntity?
}
