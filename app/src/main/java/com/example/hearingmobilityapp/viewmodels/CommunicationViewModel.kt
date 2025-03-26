package com.example.hearingmobilityapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.hearingmobilityapp.SavedMessage

class CommunicationViewModel : ViewModel() {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isListening = MutableLiveData<Boolean>()
    val isListening: LiveData<Boolean> = _isListening

    private val _savedMessages = MutableStateFlow<List<SavedMessage>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessage>> = _savedMessages

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var savedMessagesListener: ListenerRegistration? = null

    init {
        fetchSavedMessages()
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
        return auth.currentUser?.uid
    }

    private fun getSavedMessagesCollection() =
        firestore.collection("users").document(getCurrentUserId() ?: "").collection("savedMessages")

    fun saveMessage(message: String) {
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    getSavedMessagesCollection().add(hashMapOf("text" to message)).await()
                    // fetchSavedMessages() // Listener should handle updates
                } catch (e: Exception) {
                    // Handle error
                    println("Error saving message: ${e.message}")
                }
            }
        }
    }

    fun removeSavedMessage(message: String) {
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    val querySnapshot = getSavedMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()

                    for (document in querySnapshot.documents) {
                        document.reference.delete().await()
                    }
                    // fetchSavedMessages() // Listener should handle updates
                } catch (e: Exception) {
                    // Handle error
                    println("Error removing message: ${e.message}")
                }
            }
        }
    }

    private fun fetchSavedMessages() {
        getCurrentUserId()?.let { userId ->
            savedMessagesListener?.remove() // Remove previous listener

            savedMessagesListener = getSavedMessagesCollection()
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error
                        println("Listen failed: $error")
                        return@addSnapshotListener
                    }

                    val messages = snapshot?.documents?.map { document ->
                        SavedMessage(document.id, document.getString("text") ?: "")
                    } ?: emptyList()
                    _savedMessages.value = messages
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        savedMessagesListener?.remove() // Remove listener when ViewModel is cleared
    }
}
