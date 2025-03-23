package com.example.hearingmobilityapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Stopentity::class, StopTimeEntity::class, RouteEntity::class], version = 1)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }

    }
}
