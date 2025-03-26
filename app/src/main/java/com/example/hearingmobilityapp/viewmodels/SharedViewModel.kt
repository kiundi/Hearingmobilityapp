package com.example.hearingmobilityapp.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedViewModel : ViewModel() {
    private val _chatMessages = MutableStateFlow<List<String>>(emptyList())
    val chatMessages: StateFlow<List<String>> = _chatMessages

    fun addMessage(message: String) {
        _chatMessages.value = _chatMessages.value + message
    }

    fun clearMessages() {
        _chatMessages.value = emptyList()
    }
}
