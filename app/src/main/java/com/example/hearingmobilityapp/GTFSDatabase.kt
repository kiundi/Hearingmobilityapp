package com.example.hearingmobilityapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AgencyEntity::class,
        CalendarEntity::class,
        CalendarDateEntity::class,
        FrequencyEntity::class,
        RouteEntity::class,
        ShapeEntity::class,
        StopEntity::class,
        StopTimeEntity::class,
        TripEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GTFSDatabase : RoomDatabase() {
    abstract fun agencyDao(): AgencyDao
    abstract fun calendarDao(): CalendarDao
    abstract fun calendarDateDao(): CalendarDateDao
    abstract fun frequencyDao(): FrequencyDao
    abstract fun routeDao(): RouteDao
    abstract fun shapeDao(): ShapeDao
    abstract fun stopDao(): StopDao
    abstract fun stopTimeDao(): StopTimeDao
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: GTFSDatabase? = null

        fun getDatabase(context: Context): GTFSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GTFSDatabase::class.java,
                    "gtfs_database"
                )
                .addCallback(GTFSCallback(context))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GTFSCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Enable foreign key constraints
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
