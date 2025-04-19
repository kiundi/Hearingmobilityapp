package com.example.hearingmobilityapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hearingmobilityapp.SavedMessage
import com.example.hearingmobilityapp.auth.AuthService
import com.example.hearingmobilityapp.messages.MessageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isListening = MutableLiveData<Boolean>()
    val isListening: LiveData<Boolean> = _isListening

    private val _savedMessages = MutableStateFlow<List<SavedMessage>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessage>> = _savedMessages

    // Replace Firebase services with our SQLite-based services
    private val authService = AuthService(application.applicationContext)
    private val messageService = MessageService(application.applicationContext)

    // Track authentication state
    private val _isUserAuthenticated = MutableStateFlow(false)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated

    init {
        // Observe authentication state
        viewModelScope.launch {
            authService.currentUser.collectLatest { user ->
                _isUserAuthenticated.value = user != null
                
                // If user authenticated, load their messages
                if (user != null) {
                    messageService.loadSavedMessages(user.uid)
                }
            }
        }

        // Observe saved messages
        viewModelScope.launch {
            messageService.savedMessages.collectLatest { messages ->
                _savedMessages.value = messages
            }
        }
    }

    fun updateMessage(newMessage: String) {
        _message.value = newMessage
    }

    fun startListening() {
        // Implement your speech-to-text logic here and update _message.value
        _isListening.value = true
        // For now, let's simulate an update after a delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _message.value = "This is a transcribed message." // Replace with actual transcription
            _isListening.value = false
        }
    }

    fun stopListening() {
        // Implement logic to stop speech-to-text
        _isListening.value = false
    }

    private fun getCurrentUserId(): String? {
        return authService.currentUser.value?.uid
    }

    fun saveMessage(message: String) {
        val userId = getCurrentUserId() ?: return
        
        viewModelScope.launch {
            try {
                messageService.saveMessage(message, userId)
            } catch (e: Exception) {
                // Handle error
                println("Error saving message: ${e.message}")
            }
        }
    }

    fun removeSavedMessage(message: String) {
        val userId = getCurrentUserId() ?: return
        
        viewModelScope.launch {
            try {
                // Find the message ID by text
                val messageId = _savedMessages.value.find { it.text == message }?.id
                if (messageId != null) {
                    messageService.deleteMessage(messageId, userId)
                }
            } catch (e: Exception) {
                // Handle error
                println("Error removing message: ${e.message}")
            }
        }
    }
    
    fun addToFavorites(message: String): Boolean {
        val userId = getCurrentUserId() ?: return false
        
        viewModelScope.launch {
            try {
                // Find the message ID by text
                val messageId = _savedMessages.value.find { it.text == message }?.id
                if (messageId != null) {
                    messageService.updateMessageFavorite(messageId, true, userId)
                }
            } catch (e: Exception) {
                println("Error adding to favorites: ${e.message}")
            }
        }
        return true
    }

    fun removeFromFavorites(message: String): Boolean {
        val userId = getCurrentUserId() ?: return false
        
        viewModelScope.launch {
            try {
                // Find the message ID by text
                val messageId = _savedMessages.value.find { it.text == message }?.id
                if (messageId != null) {
                    messageService.updateMessageFavorite(messageId, false, userId)
                }
            } catch (e: Exception) {
                println("Error removing from favorites: ${e.message}")
            }
        }
        return true
    }

    fun isMessageFavorite(message: String, callback: (Boolean) -> Unit) {
        if (!_isUserAuthenticated.value) {
            callback(false)
            return
        }
        
        viewModelScope.launch {
            try {
                // Find the message in saved messages
                val savedMessage = _savedMessages.value.find { it.text == message }
                callback(savedMessage?.isFavorite ?: false)
            } catch (e: Exception) {
                println("Error checking if message is favorite: ${e.message}")
                callback(false)
            }
        }
    }
}
