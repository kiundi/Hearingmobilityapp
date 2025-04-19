package com.example.hearingmobilityapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hearingmobilityapp.auth.AuthService
import com.example.hearingmobilityapp.messages.MessageService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    companion object {
        private const val TAG = "CommunicationViewModel"
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _savedMessages = MutableStateFlow<List<SavedMessage>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessage>> = _savedMessages

    private val _favoriteMessages = MutableStateFlow<List<SavedMessage>>(emptyList())
    val favoriteMessages: StateFlow<List<SavedMessage>> = _favoriteMessages

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

    private val previousRoutes = mutableListOf<SavedRoute>()
    private val gtfsHelper = GTFSHelper(application.applicationContext)
    
    // Database initialization state
    private val _isDatabaseReady = MutableStateFlow(false)
    val isDatabaseReady: StateFlow<Boolean> = _isDatabaseReady

    // Model initialization status
    private val _modelInitStatus = MutableStateFlow(ModelInitStatus.NOT_INITIALIZED)
    val modelInitStatus: StateFlow<ModelInitStatus> = _modelInitStatus

    // Replace Firebase with our services
    private val authService = AuthService(application.applicationContext)
    private val messageService = MessageService(application.applicationContext)

    // Add a state to track if user is authenticated
    private val _isUserAuthenticated = MutableStateFlow(false)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated

    // Add these properties to store last entered route
    private val _lastSource = MutableStateFlow("")
    val lastSource: StateFlow<String> = _lastSource

    private val _lastDestination = MutableStateFlow("")
    val lastDestination: StateFlow<String> = _lastDestination

    init {
        // Observe authentication state
        viewModelScope.launch {
            authService.currentUser.collectLatest { user ->
                _isUserAuthenticated.value = user != null
            }
        }

        // Observe saved messages
        viewModelScope.launch {
            messageService.savedMessages.collectLatest { messages ->
                _savedMessages.value = messages
                
                // Update favorites
                _favoriteMessages.value = messages.filter { it.isFavorite }
            }
        }

        // Only fetch messages if user is authenticated
        viewModelScope.launch {
            isUserAuthenticated.collect { isAuthenticated ->
                if (isAuthenticated) {
                    val userId = authService.currentUser.value?.uid
                    if (userId != null) {
                        messageService.loadSavedMessages(userId)
                    }
                }
            }
        }
        
        // Monitor GTFSHelper database initialization state
        viewModelScope.launch {
            gtfsHelper.isDatabaseInitialized.collect { isInitialized ->
                _isDatabaseReady.value = isInitialized
                if (isInitialized) {
                    Log.d(TAG, "GTFS database is now initialized and ready")
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
                    Log.d(TAG, "Updated message with transcribed text: '$transcribedText'")
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
        viewModelScope.launch {
            Log.d(TAG, "stopListening called")
            voiceRecognitionManager.stopRecording()

            // Get current transcribed text and update message if not empty
            val transcribedText = voiceRecognitionManager.transcribedText.value
            if (transcribedText.isNotEmpty()) {
                // Treat transcribed text as a new message
                updateMessage(transcribedText)
                // No longer save transcribed message to database
                Log.d(TAG, "Updated message with transcribed text: '$transcribedText'")
                
                // Auto-save the message if it's meaningful (more than just a few characters)
                if (transcribedText.length > 5) {
                    saveMessage(transcribedText)
                }
            } else {
                Log.e(TAG, "No transcribed text available")
                // If no transcribed text available but we have partial text, use that instead
                val partialText = voiceRecognitionManager.partialText.value
                if (partialText.isNotEmpty()) {
                    updateMessage(partialText)
                    Log.d(TAG, "Used partial text instead: '$partialText'")
                }
            }

            _isListening.value = false
        }
    }

    fun saveMessage(message: String) {
        viewModelScope.launch {
            if (message.isBlank()) {
                Log.e(TAG, "Cannot save empty message")
                return@launch
            }

            val userId = authService.currentUser.value?.uid
            if (userId == null) {
                Log.e(TAG, "Cannot save message, user not authenticated")
                return@launch
            }

            try {
                val messageId = messageService.saveMessage(message, userId)
                Log.d(TAG, "Message saved with ID: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving message: ${e.message}", e)
            }
        }
    }

    fun removeSavedMessage(message: String) {
        viewModelScope.launch {
            try {
                // Find the message ID by text
                val messageId = _savedMessages.value.find { it.text == message }?.id
                if (messageId != null) {
                    val userId = authService.currentUser.value?.uid
                    if (userId != null) {
                        messageService.deleteMessage(messageId, userId)
                        Log.d(TAG, "Removed message with ID: $messageId")
                    }
                } else {
                    Log.e(TAG, "Could not find message to remove: $message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing message: ${e.message}", e)
            }
        }
    }

    fun updateLastRoute(source: String, destination: String) {
        _lastSource.value = source
        _lastDestination.value = destination
    }
    
    fun addToFavorites(message: String): Boolean {
        if (!_isUserAuthenticated.value) {
            return false
        }
        
        val userId = authService.currentUser.value?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot add to favorites, user not authenticated")
            return false
        }

        viewModelScope.launch {
            try {
                // Check if message already exists in saved messages
                var messageId = _savedMessages.value.find { it.text == message }?.id
                
                // If message doesn't exist, save it first
                if (messageId == null) {
                    Log.d(TAG, "Message doesn't exist in saved messages, saving first: '$message'")
                    messageId = messageService.saveMessage(message, userId)
                    
                    // Wait for a moment to ensure message is saved and state is updated
                    delay(500)
                    
                    // Refresh messageId after saving
                    messageId = _savedMessages.value.find { it.text == message }?.id
                }
                
                if (messageId != null) {
                    val success = messageService.updateMessageFavorite(messageId, true, userId)
                    if (success) {
                        // Show notification
                        _showAddedToFavoritesMessage.value = true
                        viewModelScope.launch {
                            delay(2000)
                            _showAddedToFavoritesMessage.value = false
                        }
                        Log.d(TAG, "Added message to favorites successfully: '$message'")
                    } else {
                        Log.e(TAG, "Failed to update favorite status for message: '$message'")
                    }
                } else {
                    Log.e(TAG, "Could not find message to add to favorites: '$message'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to favorites: ${e.message}", e)
            }
        }
        return true
    }

    fun removeFromFavorites(message: String): Boolean {
        if (!_isUserAuthenticated.value) {
            return false
        }

        val userId = authService.currentUser.value?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot remove from favorites, user not authenticated")
            return false
        }

        viewModelScope.launch {
            try {
                // Find the message ID by text
                val messageId = _savedMessages.value.find { it.text == message }?.id
                if (messageId != null) {
                    val success = messageService.updateMessageFavorite(messageId, false, userId)
                    if (success) {
                        // Show notification
                        _showRemovedFromFavoritesMessage.value = true
                        viewModelScope.launch {
                            delay(2000)
                            _showRemovedFromFavoritesMessage.value = false
                        }
                    }
                } else {
                    Log.e(TAG, "Could not find message to remove from favorites: $message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from favorites: ${e.message}", e)
            }
        }
        return true
    }
    
    private fun getCurrentUserId(): String? {
        return authService.currentUser.value?.uid
    }

    fun isMessageFavorite(message: String, callback: (Boolean) -> Unit) {
        if (!_isUserAuthenticated.value) {
            callback(false)
            return
        }
        
        val userId = authService.currentUser.value?.uid
        if (userId == null) {
            callback(false)
            return
        }

        viewModelScope.launch {
            try {
                // Find the message in saved messages
                val savedMessage = _savedMessages.value.find { it.text == message }
                callback(savedMessage?.isFavorite ?: false)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if message is favorite: ${e.message}", e)
                callback(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognitionManager.release() // Release voice recognition resources
    }

    fun saveRoute(source: String, destination: String) {
        val route = SavedRoute(
            id = UUID.randomUUID().toString(),
            source = source,
            destination = destination,
            timestamp = System.currentTimeMillis()
        )
        previousRoutes.add(route)
        
        // Update the StateFlow to notify observers
        _savedRoutes.value = previousRoutes.sortedByDescending { it.timestamp }
        
        // Store as last entered route
        _lastSource.value = source
        _lastDestination.value = destination
    }
    
    /**
     * Get a list of saved routes as formatted strings for display
     */
    fun getSavedRoutes(): List<String> {
        return previousRoutes.map { route ->
            "${route.source}|${route.destination}"
        }
    }

    fun getPreviousRoutes(): List<SavedRoute> {
        return previousRoutes.sortedByDescending { it.timestamp }
    }

    suspend fun getRouteInfo(source: String, destination: String): String {
        Log.d("CommunicationViewModel", "Getting route info from '$source' to '$destination'")
        return try {
            if (source.isBlank() || destination.isBlank()) {
                Log.w("CommunicationViewModel", "Empty source or destination provided")
                "Please provide both source and destination locations."
            } else {
                // Wait for database to be initialized
                val isInitialized = gtfsHelper.isDatabaseInitialized.first()
                
                val routeInfo = gtfsHelper.getRouteInfo(source, destination)
                routeInfo?.let { "Route: ${it.routeName} (${it.routeDescription})\nNext departure: ${it.nextDeparture}" } 
                    ?: "No route information available"
            }
        } catch (e: Exception) {
            Log.e("CommunicationViewModel", "Error in getRouteInfo: ${e.message}", e)
            "Sorry, I couldn't get route information at this time. Please try again later."
        }
    }

    suspend fun getStopInfo(stopName: String): String {
        Log.d("CommunicationViewModel", "Getting stop info for '$stopName'")
        return try {
            if (stopName.isBlank()) {
                Log.w("CommunicationViewModel", "Empty stop name provided")
                "Please provide a stop name."
            } else {
                // Wait for database to be initialized
                val isInitialized = gtfsHelper.isDatabaseInitialized.first()
                
                val stopInfo = gtfsHelper.getStopInfo(stopName)
                stopInfo?.let { "Next arrivals at $stopName:\n${it.arrivals.joinToString("\n")}" } 
                    ?: "No upcoming arrivals available"
            }
        } catch (e: Exception) {
            Log.e("CommunicationViewModel", "Error in getStopInfo: ${e.message}", e)
            "Sorry, I couldn't get stop information at this time. Please try again later."
        }
    }

    suspend fun getStopCoordinates(stopName: String): Pair<Double, Double> {
        Log.d("CommunicationViewModel", "Getting coordinates for stop '$stopName'")
        return try {
            if (stopName.isBlank()) {
                Log.w("CommunicationViewModel", "Empty stop name provided for coordinates")
                Pair(0.0, 0.0)
            } else {
                // Wait for database to be initialized
                val isInitialized = gtfsHelper.isDatabaseInitialized.first()
                
                gtfsHelper.getStopCoordinates(stopName) ?: Pair(0.0, 0.0)
            }
        } catch (e: Exception) {
            Log.e("CommunicationViewModel", "Error in getStopCoordinates: ${e.message}", e)
            Pair(0.0, 0.0)
        }
    }

    suspend fun getRoutePoints(source: String, destination: String): List<Pair<Double, Double>> {
        Log.d("CommunicationViewModel", "Getting route points from '$source' to '$destination'")
        return try {
            if (source.isBlank() || destination.isBlank()) {
                Log.w("CommunicationViewModel", "Empty source or destination provided for route points")
                emptyList()
            } else {
                // Wait for database to be initialized
                val isInitialized = gtfsHelper.isDatabaseInitialized.first()
                
                gtfsHelper.getRoutePoints(source, destination)
            }
        } catch (e: Exception) {
            Log.e("CommunicationViewModel", "Error in getRoutePoints: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getRouteTime(source: String, destination: String): String {
        Log.d("CommunicationViewModel", "Getting route time from '$source' to '$destination'")
        return try {
            if (source.isBlank() || destination.isBlank()) {
                Log.w("CommunicationViewModel", "Empty source or destination provided for route time")
                "Please provide both source and destination locations."
            } else {
                // Wait for database to be initialized
                val isInitialized = gtfsHelper.isDatabaseInitialized.first()
                
                gtfsHelper.getRouteTime(source, destination)
            }
        } catch (e: Exception) {
            Log.e("CommunicationViewModel", "Error in getRouteTime: ${e.message}", e)
            "Sorry, I couldn't calculate the route time at this time. Please try again later."
        }
    }

    fun removeRoute(routeId: String) {
        previousRoutes.removeAll { it.id == routeId }
        
        // Update the StateFlow to notify observers
        _savedRoutes.value = previousRoutes.sortedByDescending { it.timestamp }
    }

    fun getGTFSHelper(): GTFSHelper {
        return gtfsHelper
    }

    // isDatabaseReady property is already defined above

    // Add this function to handle auth state checks
    private fun checkAuthState(): Boolean {
        return _isUserAuthenticated.value
    }
}

enum class ModelInitStatus {
    NOT_INITIALIZED,
    INITIALIZING,
    INITIALIZED,
    FAILED
}