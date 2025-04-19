package com.example.hearingmobilityapp.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.hearingmobilityapp.SavedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "hearing_mobility_app.db"
        private const val DATABASE_VERSION = 1

        // Tables
        private const val TABLE_USERS = "users"
        private const val TABLE_SAVED_MESSAGES = "saved_messages"

        // Common Columns
        private const val COLUMN_ID = "id"
        
        // User Table Columns
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD_HASH = "password_hash"
        private const val COLUMN_NAME = "name"
        
        // SavedMessage Table Columns
        private const val COLUMN_TEXT = "text"
        private const val COLUMN_ROUTE_ID = "route_id"
        private const val COLUMN_ROUTE_NAME = "route_name"
        private const val COLUMN_ROUTE_DETAILS = "route_details"
        private const val COLUMN_ROUTE_START_LOCATION = "route_start_location"
        private const val COLUMN_ROUTE_END_LOCATION = "route_end_location"
        private const val COLUMN_ROUTE_DESTINATION_TYPE = "route_destination_type"
        private const val COLUMN_USER_ID = "user_id"
        private const val COLUMN_IS_FAVORITE = "is_favorite"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create users table
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_EMAIL TEXT UNIQUE,
                $COLUMN_PASSWORD_HASH TEXT,
                $COLUMN_NAME TEXT
            )
        """.trimIndent()

        // Create saved messages table
        val createSavedMessagesTable = """
            CREATE TABLE $TABLE_SAVED_MESSAGES (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_TEXT TEXT,
                $COLUMN_ROUTE_ID TEXT,
                $COLUMN_ROUTE_NAME TEXT,
                $COLUMN_ROUTE_DETAILS TEXT,
                $COLUMN_ROUTE_START_LOCATION TEXT,
                $COLUMN_ROUTE_END_LOCATION TEXT,
                $COLUMN_ROUTE_DESTINATION_TYPE TEXT,
                $COLUMN_USER_ID TEXT,
                $COLUMN_IS_FAVORITE INTEGER DEFAULT 0,
                FOREIGN KEY($COLUMN_USER_ID) REFERENCES $TABLE_USERS($COLUMN_ID)
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createSavedMessagesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple upgrade strategy - drop and recreate tables
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAVED_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // User methods
    suspend fun registerUser(email: String, passwordHash: String, name: String): String {
        return withContext(Dispatchers.IO) {
            val userId = UUID.randomUUID().toString()
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_ID, userId)
                put(COLUMN_EMAIL, email)
                put(COLUMN_PASSWORD_HASH, passwordHash)
                put(COLUMN_NAME, name)
            }
            db.insert(TABLE_USERS, null, values)
            userId
        }
    }

    suspend fun getUserByEmail(email: String): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_USERS,
                arrayOf(COLUMN_ID, COLUMN_PASSWORD_HASH),
                "$COLUMN_EMAIL = ?",
                arrayOf(email),
                null,
                null,
                null
            )

            var result: Pair<String, String>? = null
            if (cursor.moveToFirst()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val passwordHash = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD_HASH))
                result = Pair(id, passwordHash)
            }
            cursor.close()
            result
        }
    }

    suspend fun getUserById(userId: String): Map<String, String>? {
        return withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_USERS,
                arrayOf(COLUMN_ID, COLUMN_EMAIL, COLUMN_NAME),
                "$COLUMN_ID = ?",
                arrayOf(userId),
                null,
                null,
                null
            )

            var result: Map<String, String>? = null
            if (cursor.moveToFirst()) {
                result = mapOf(
                    "id" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    "email" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                    "name" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
                )
            }
            cursor.close()
            result
        }
    }

    // Saved Messages methods
    suspend fun saveMessage(message: SavedMessage, userId: String): String {
        return withContext(Dispatchers.IO) {
            val db = writableDatabase
            val messageId = message.id.ifEmpty { UUID.randomUUID().toString() }
            
            val values = ContentValues().apply {
                put(COLUMN_ID, messageId)
                put(COLUMN_TEXT, message.text)
                put(COLUMN_ROUTE_ID, message.routeId)
                put(COLUMN_ROUTE_NAME, message.routeName)
                put(COLUMN_ROUTE_DETAILS, message.routeDetails)
                put(COLUMN_ROUTE_START_LOCATION, message.routeStartLocation)
                put(COLUMN_ROUTE_END_LOCATION, message.routeEndLocation)
                put(COLUMN_ROUTE_DESTINATION_TYPE, message.routeDestinationType)
                put(COLUMN_USER_ID, userId)
            }

            db.insertWithOnConflict(TABLE_SAVED_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            messageId
        }
    }

    suspend fun getAllSavedMessages(userId: String): List<SavedMessage> {
        return withContext(Dispatchers.IO) {
            val messages = mutableListOf<SavedMessage>()
            val db = readableDatabase
            val cursor = db.query(
                TABLE_SAVED_MESSAGES,
                null,
                "$COLUMN_USER_ID = ?",
                arrayOf(userId),
                null,
                null,
                null
            )

            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    val text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT))
                    val routeId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_ID))
                    val routeName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_NAME))
                    val routeDetails = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_DETAILS))
                    val routeStartLocation = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_START_LOCATION))
                    val routeEndLocation = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_END_LOCATION))
                    val routeDestinationType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_DESTINATION_TYPE))
                    val isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_FAVORITE)) == 1
                    
                    messages.add(
                        SavedMessage(
                            id = id,
                            text = text,
                            routeId = routeId,
                            routeName = routeName,
                            routeDetails = routeDetails,
                            routeStartLocation = routeStartLocation,
                            routeEndLocation = routeEndLocation,
                            routeDestinationType = routeDestinationType,
                            isFavorite = isFavorite
                        )
                    )
                } while (cursor.moveToNext())
            }
            
            cursor.close()
            messages
        }
    }

    suspend fun deleteMessage(messageId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val db = writableDatabase
            val deletedRows = db.delete(
                TABLE_SAVED_MESSAGES, 
                "$COLUMN_ID = ?",
                arrayOf(messageId)
            )
            deletedRows > 0
        }
    }

    suspend fun updateMessageFavoriteStatus(messageId: String, isFavorite: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_IS_FAVORITE, if (isFavorite) 1 else 0)
            }
            
            val updatedRows = db.update(
                TABLE_SAVED_MESSAGES,
                values,
                "$COLUMN_ID = ?",
                arrayOf(messageId)
            )
            
            updatedRows > 0
        }
    }
} 