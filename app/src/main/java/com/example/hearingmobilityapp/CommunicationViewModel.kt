package com.example.hearingmobilityapp

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

data class SavedMessages(val id: String, val text: String, val isFavorite: Boolean = false)

class CommunicationViewModel : ViewModel() {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isListening = MutableLiveData<Boolean>()
    val isListening: LiveData<Boolean> = _isListening

    private val _savedMessages = MutableStateFlow<List<SavedMessages>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessages>> = _savedMessages
    
    private val _favoriteMessages = MutableStateFlow<List<SavedMessages>>(emptyList())
    val favoriteMessages: StateFlow<List<SavedMessages>> = _favoriteMessages
    
    private val _showAddedToFavoritesMessage = MutableStateFlow(false)
    val showAddedToFavoritesMessage: StateFlow<Boolean> = _showAddedToFavoritesMessage
    
    private val _showRemovedFromFavoritesMessage = MutableStateFlow(false)
    val showRemovedFromFavoritesMessage: StateFlow<Boolean> = _showRemovedFromFavoritesMessage

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var savedMessagesListener: ListenerRegistration? = null
    private var favoriteMessagesListener: ListenerRegistration? = null
    
    // Add a state to track if user is authenticated
    private val _isUserAuthenticated = MutableStateFlow(false)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated

    init {
        // Check if user is already signed in
        checkAuthState()
        
        // Only fetch messages if user is authenticated
        viewModelScope.launch {
            isUserAuthenticated.collect { isAuthenticated ->
                if (isAuthenticated) {
                    fetchSavedMessages()
                    fetchFavoriteMessages()
                }
            }
        }
    }
    
    private fun checkAuthState() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // If no user is signed in, sign in anonymously
            viewModelScope.launch {
                try {
                    auth.signInAnonymously().await()
                    _isUserAuthenticated.value = true
                } catch (e: Exception) {
                    println("Error signing in anonymously: ${e.message}")
                }
            }
        } else {
            _isUserAuthenticated.value = true
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
        return auth.currentUser?.uid
    }

    private fun getSavedMessagesCollection() =
        firestore.collection("users").document(getCurrentUserId() ?: "").collection("savedMessages")
        
    private fun getFavoriteMessagesCollection() =
        firestore.collection("users").document(getCurrentUserId() ?: "").collection("favoriteMessages")

    fun saveMessage(message: String) {
        if (!_isUserAuthenticated.value) {
            checkAuthState() // Try to authenticate if not already
            return
        }
        
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    getSavedMessagesCollection().add(hashMapOf("text" to message, "isFavorite" to false)).await()
                    // fetchSavedMessages() // Listener should handle updates
                } catch (e: Exception) {
                    // Handle error
                    println("Error saving message: ${e.message}")
                }
            }
        }
    }
    
    fun addToFavorites(message: String) {
        if (!_isUserAuthenticated.value) {
            checkAuthState() // Try to authenticate if not already
            return
        }
        
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    // First check if the message already exists in saved messages
                    val querySnapshot = getSavedMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()
                    
                    // If it exists, update it to mark as favorite
                    if (!querySnapshot.isEmpty) {
                        for (document in querySnapshot.documents) {
                            document.reference.update("isFavorite", true).await()
                        }
                    } else {
                        // If it doesn't exist in saved messages, add it
                        getSavedMessagesCollection().add(hashMapOf(
                            "text" to message,
                            "isFavorite" to true
                        )).await()
                    }
                    
                    // Also add to favorites collection for easier querying
                    getFavoriteMessagesCollection().add(hashMapOf("text" to message)).await()
                    
                    // Show notification
                    _showAddedToFavoritesMessage.value = true
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        _showAddedToFavoritesMessage.value = false
                    }
                    
                } catch (e: Exception) {
                    println("Error adding to favorites: ${e.message}")
                }
            }
        }
    }
    
    fun removeFromFavorites(message: String) {
        if (!_isUserAuthenticated.value) {
            checkAuthState() // Try to authenticate if not already
            return
        }
        
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    // Update saved messages to mark as not favorite
                    val savedQuerySnapshot = getSavedMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()
                    
                    for (document in savedQuerySnapshot.documents) {
                        document.reference.update("isFavorite", false).await()
                    }
                    
                    // Remove from favorites collection
                    val favQuerySnapshot = getFavoriteMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()
                    
                    for (document in favQuerySnapshot.documents) {
                        document.reference.delete().await()
                    }
                    
                    // Show notification
                    _showRemovedFromFavoritesMessage.value = true
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        _showRemovedFromFavoritesMessage.value = false
                    }
                    
                } catch (e: Exception) {
                    println("Error removing from favorites: ${e.message}")
                }
            }
        }
    }
    
    // Function to completely remove a saved message
    fun removeSavedMessage(message: String) {
        if (!_isUserAuthenticated.value) {
            checkAuthState() // Try to authenticate if not already
            return
        }
        
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    // First check if it's a favorite and remove from favorites collection if needed
                    val favQuerySnapshot = getFavoriteMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()
                    
                    for (document in favQuerySnapshot.documents) {
                        document.reference.delete().await()
                    }
                    
                    // Then remove from saved messages collection
                    val savedQuerySnapshot = getSavedMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()
                    
                    for (document in savedQuerySnapshot.documents) {
                        document.reference.delete().await()
                    }
                    
                    // Update the local lists
                    fetchSavedMessages()
                    fetchFavoriteMessages()
                    
                } catch (e: Exception) {
                    println("Error removing saved message: ${e.message}")
                }
            }
        }
    }

    fun isMessageFavorite(message: String, callback: (Boolean) -> Unit) {
        if (!_isUserAuthenticated.value) {
            callback(false)
            checkAuthState() // Try to authenticate if not already
            return
        }
        
        getCurrentUserId()?.let { userId ->
            viewModelScope.launch {
                try {
                    val querySnapshot = getFavoriteMessagesCollection()
                        .whereEqualTo("text", message)
                        .get()
                        .await()
                    
                    callback(!querySnapshot.isEmpty)
                } catch (e: Exception) {
                    callback(false)
                    println("Error checking if message is favorite: ${e.message}")
                }
            }
        } ?: callback(false)
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
                        SavedMessages(
                            id = document.id, 
                            text = document.getString("text") ?: "",
                            isFavorite = document.getBoolean("isFavorite") ?: false
                        )
                    } ?: emptyList()
                    _savedMessages.value = messages
                }
        }
    }
    
    private fun fetchFavoriteMessages() {
        getCurrentUserId()?.let { userId ->
            favoriteMessagesListener?.remove() // Remove previous listener

            favoriteMessagesListener = getFavoriteMessagesCollection()
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error
                        println("Listen failed for favorites: $error")
                        return@addSnapshotListener
                    }

                    val messages = snapshot?.documents?.map { document ->
                        SavedMessages(
                            id = document.id, 
                            text = document.getString("text") ?: "",
                            isFavorite = true
                        )
                    } ?: emptyList()
                    _favoriteMessages.value = messages
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        savedMessagesListener?.remove() // Remove listener when ViewModel is cleared
        favoriteMessagesListener?.remove()
    }
}