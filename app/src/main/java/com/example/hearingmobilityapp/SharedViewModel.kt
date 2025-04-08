package com.example.hearingmobilityapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedViewModel : ViewModel() {
    private val _chatMessages = MutableStateFlow<List<String>>(emptyList())
    val chatMessages: StateFlow<List<String>> = _chatMessages
    
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message
    
    private val _tripInfo = MutableStateFlow("")
    val tripInfo: StateFlow<String> = _tripInfo
    
    fun updateMessage(newMessage: String) {
        _message.value = newMessage
    }

    fun addMessage(message: String) {
        _chatMessages.value = _chatMessages.value + message
        // Also update the current message
        updateMessage(message)
    }

    fun clearMessages() {
        _chatMessages.value = emptyList()
    }

    fun updateTripInfo(info: String) {
        _tripInfo.value = info
    }
}
