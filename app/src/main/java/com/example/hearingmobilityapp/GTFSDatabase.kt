package com.example.hearingmobilityapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Stopentity::class,
        StopTimeEntity::class,
        RouteEntity::class,
        TripEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GTFSDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
