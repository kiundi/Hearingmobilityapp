package com.example.hearingmobilityapp.messages

import android.content.Context
import android.util.Log
import com.example.hearingmobilityapp.SavedMessage
import com.example.hearingmobilityapp.database.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class MessageService(context: Context) {
    companion object {
        private const val TAG = "MessageService"
    }

    private val dbHelper = DatabaseHelper(context)
    
    // Messages state
    private val _savedMessages = MutableStateFlow<List<SavedMessage>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessage>> = _savedMessages
    
    suspend fun loadSavedMessages(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val messages = dbHelper.getAllSavedMessages(userId)
                _savedMessages.value = messages
                Log.d(TAG, "Loaded ${messages.size} saved messages for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved messages: ${e.message}", e)
            }
        }
    }
    
    suspend fun saveMessage(message: String, userId: String, routeInfo: RouteInfo? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val savedMessage = SavedMessage(
                    id = "",  // Empty ID will be replaced with a generated UUID
                    text = message,
                    routeId = routeInfo?.id,
                    routeName = routeInfo?.name,
                    routeDetails = routeInfo?.details,
                    routeStartLocation = routeInfo?.startLocation,
                    routeEndLocation = routeInfo?.endLocation,
                    routeDestinationType = routeInfo?.destinationType
                )
                
                val messageId = dbHelper.saveMessage(savedMessage, userId)
                
                // Update the state with the new message
                refreshMessages(userId)
                
                messageId
            } catch (e: Exception) {
                Log.e(TAG, "Error saving message: ${e.message}", e)
                null
            }
        }
    }
    
    suspend fun deleteMessage(messageId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val success = dbHelper.deleteMessage(messageId)
                if (success) {
                    // Update the state by removing the deleted message
                    _savedMessages.update { currentMessages ->
                        currentMessages.filter { it.id != messageId }
                    }
                    Log.d(TAG, "Message $messageId deleted successfully")
                } else {
                    Log.w(TAG, "Failed to delete message $messageId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting message: ${e.message}", e)
            }
        }
    }
    
    suspend fun updateMessageFavorite(messageId: String, isFavorite: Boolean, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val success = dbHelper.updateMessageFavoriteStatus(messageId, isFavorite)
                if (success) {
                    refreshMessages(userId)
                    Log.d(TAG, "Updated favorite status for message $messageId to $isFavorite")
                } else {
                    Log.w(TAG, "Failed to update favorite status for message $messageId")
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error updating message favorite status: ${e.message}", e)
                false
            }
        }
    }
    
    private suspend fun refreshMessages(userId: String) {
        loadSavedMessages(userId)
    }
}

data class RouteInfo(
    val id: String,
    val name: String,
    val details: String,
    val startLocation: String,
    val endLocation: String,
    val destinationType: String
) 