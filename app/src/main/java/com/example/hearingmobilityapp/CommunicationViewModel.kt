package com.example.hearingmobilityapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import java.util.UUID

data class SavedMessages(val id: String, val text: String, val isFavorite: Boolean = false)

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _savedMessages = MutableStateFlow<List<SavedMessages>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessages>> = _savedMessages

    private val _favoriteMessages = MutableStateFlow<List<SavedMessages>>(emptyList())
    val favoriteMessages: StateFlow<List<SavedMessages>> = _favoriteMessages

    private val _showAddedToFavoritesMessage = MutableStateFlow(false)
    val showAddedToFavoritesMessage: StateFlow<Boolean> = _showAddedToFavoritesMessage

    private val _showRemovedFromFavoritesMessage = MutableStateFlow(false)
    val showRemovedFromFavoritesMessage: StateFlow<Boolean> = _showRemovedFromFavoritesMessage

    // Saved routes functionality
    private val _savedRoutes = MutableStateFlow<List<SavedRoute>>(emptyList())
    val savedRoutes: StateFlow<List<SavedRoute>> = _savedRoutes

    // Voice recognition manager
    private val voiceRecognitionManager = VoiceRecognitionManager(application.applicationContext)

    // Recording duration
    private val _recordingDuration = MutableStateFlow("")
    val recordingDuration: StateFlow<String> = _recordingDuration

    // Partial transcription (updates as user speaks)
    private val _partialTranscription = MutableStateFlow("")
    val partialTranscription: StateFlow<String> = _partialTranscription

    /**
     * Save a route to the saved routes list
     */
    fun saveRoute(startLocation: String, endLocation: String, destinationType: String) {
        val newRoute = SavedRoute(
            startLocation = startLocation,
            endLocation = endLocation,
            destinationType = destinationType
        )
        _savedRoutes.value = _savedRoutes.value + newRoute
        saveRouteToFirestore(newRoute)
    }

    /**
     * Remove a route from the saved routes list
     */
    fun removeRoute(routeId: String) {
        _savedRoutes.value = _savedRoutes.value.filter { it.id != routeId }
        removeRouteFromFirestore(routeId)
    }

    /**
     * Save a route to Firestore
     */
    private fun saveRouteToFirestore(route: SavedRoute) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users")
                .document(user.uid)
                .collection("routes")
                .document(route.id)
                .set(route)
                .addOnSuccessListener {
                    Log.d("CommunicationViewModel", "Route saved to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("CommunicationViewModel", "Error saving route", e)
                }
        }
    }

    /**
     * Remove a route from Firestore
     */
    private fun removeRouteFromFirestore(routeId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users")
                .document(user.uid)
                .collection("routes")
                .document(routeId)
                .delete()
                .addOnSuccessListener {
                    Log.d("CommunicationViewModel", "Route removed from Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("CommunicationViewModel", "Error removing route", e)
                }
        }
    }

    /**
     * Load saved routes from Firestore
     */
    fun loadSavedRoutes() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users")
                .document(user.uid)
                .collection("routes")
                .get()
                .addOnSuccessListener { documents ->
                    val routes = documents.mapNotNull { it.toObject(SavedRoute::class.java) }
                    _savedRoutes.value = routes
                }
                .addOnFailureListener { e ->
                    Log.e("CommunicationViewModel", "Error loading routes", e)
                }
        }
    }

    // Model initialization status
    private val _modelInitStatus = MutableStateFlow(ModelInitStatus.NOT_INITIALIZED)
    val modelInitStatus: StateFlow<ModelInitStatus> = _modelInitStatus

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

        // Initialize voice recognition model
        initializeVoiceRecognition()

        // Collect recording state
        viewModelScope.launch {
            voiceRecognitionManager.isRecording.collect { isRecording ->
                _isListening.value = isRecording
            }
        }

        // Collect model initialization state
        viewModelScope.launch {
            voiceRecognitionManager.isModelInitialized.collect { isInitialized ->
                if (isInitialized) {
                    _modelInitStatus.value = ModelInitStatus.INITIALIZED
                }
            }
        }

        // Collect recording duration
        viewModelScope.launch {
            voiceRecognitionManager.recordingDuration.collect { durationInSeconds ->
                _recordingDuration.value = voiceRecognitionManager.formatDuration(durationInSeconds)
            }
        }

        // Collect partial transcription
        viewModelScope.launch {
            voiceRecognitionManager.partialText.collect { partialText ->
                _partialTranscription.value = partialText
            }
        }

        // Collect final transcription
        viewModelScope.launch {
            voiceRecognitionManager.transcribedText.collect { transcribedText ->
                if (transcribedText.isNotEmpty()) {
                    _message.value = transcribedText
                }
            }
        }
    }

    private fun initializeVoiceRecognition() {
        viewModelScope.launch {
            Log.d("CommunicationViewModel", "Starting model initialization...")
            _modelInitStatus.value = ModelInitStatus.INITIALIZING
            try {
                val modelInitialized = voiceRecognitionManager.initModel()
                if (modelInitialized) {
                    Log.d("CommunicationViewModel", "Model initialized successfully")
                    _modelInitStatus.value = ModelInitStatus.INITIALIZED
                } else {
                    Log.e("CommunicationViewModel", "Model initialization failed")
                    _modelInitStatus.value = ModelInitStatus.FAILED
                    // Schedule a retry after a delay
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000) // wait 3 seconds
                        if (_modelInitStatus.value == ModelInitStatus.FAILED) {
                            Log.d("CommunicationViewModel", "Retrying model initialization...")
                            initializeVoiceRecognition()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CommunicationViewModel", "Exception during model initialization: ${e.message}", e)
                _modelInitStatus.value = ModelInitStatus.FAILED
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
        Log.d("CommunicationViewModel", "startListening called, model status: ${_modelInitStatus.value}")
        if (_modelInitStatus.value != ModelInitStatus.INITIALIZED) {
            // Try to initialize the model again if it failed
            if (_modelInitStatus.value == ModelInitStatus.FAILED) {
                Log.d("CommunicationViewModel", "Model initialization previously failed, retrying...")
                initializeVoiceRecognition()
            } else {
                Log.d("CommunicationViewModel", "Model is not initialized yet, current status: ${_modelInitStatus.value}")
            }
            return
        }

        Log.d("CommunicationViewModel", "Starting voice recognition...")
        voiceRecognitionManager.startRecording()
    }

    fun stopListening() {
        Log.d("CommunicationViewModel", "stopListening called")
        voiceRecognitionManager.stopRecording()

        // Get the transcribed text and update the message
        val transcribedText = voiceRecognitionManager.transcribedText.value
        if (transcribedText.isNotEmpty()) {
            Log.d("CommunicationViewModel", "Transcribed text: $transcribedText")
            _message.value = transcribedText
        } else {
            Log.d("CommunicationViewModel", "No transcribed text available")
        }
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
        voiceRecognitionManager.release() // Release voice recognition resources
    }
}


enum class ModelInitStatus {
    NOT_INITIALIZED,
    INITIALIZING,
    INITIALIZED,
    FAILED
}