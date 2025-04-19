package com.example.hearingmobilityapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manager class for handling voice recognition using Android's built-in SpeechRecognizer
 */
class VoiceRecognitionManager(private val context: Context) {
    companion object {
        private const val TAG = "VoiceRecognitionManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    
    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    
    // Transcribed text
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText
    
    // Partial transcribed text (updates as user speaks)
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText
    
    // Recording duration in seconds
    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration: StateFlow<Int> = _recordingDuration
    
    // Model initialization state - always true for system speech recognizer
    private val _isModelInitialized = MutableStateFlow(true)
    val isModelInitialized: StateFlow<Boolean> = _isModelInitialized
    
    // Recording timer
    private val handler = Handler(Looper.getMainLooper())
    private var durationInSeconds = 0
    private val updateTimer = object : Runnable {
        override fun run() {
            if (_isRecording.value) {
                durationInSeconds++
                _recordingDuration.value = durationInSeconds
                handler.postDelayed(this, 1000)
            }
        }
    }

    /**
     * Initialize the speech recognition
     * @return true if initialization was successful, false otherwise
     */
    suspend fun initModel(): Boolean {
        try {
            // Check if device supports speech recognition
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition not available on this device")
                _isModelInitialized.value = false
                return false
            }
            
            // Create recognizer
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
            
            _isModelInitialized.value = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer: ${e.message}", e)
            _isModelInitialized.value = false
            return false
        }
    }

    /**
     * Start recording and recognizing speech
     */
    fun startRecording() {
        Log.d(TAG, "startRecording called")
        if (_isRecording.value) {
            Log.d(TAG, "Already recording")
            return
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        try {
            // First clean up any existing recognizer to avoid conflicts
            release()
            
            // Create a new recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            // Prepare the recognition intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Use longer silence timeouts for better recognition
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                // No beep sounds
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0)
            }
            
            // Set up the listener
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            
            // Reset values
            _transcribedText.value = ""
            _partialText.value = ""
            durationInSeconds = 0
            _recordingDuration.value = 0

            // Update recording state first to ensure UI updates
            _isRecording.value = true
            
            // Start timer
            handler.post(updateTimer)
            
            // Start listening after setting up state
            speechRecognizer?.startListening(intent)
            
            Log.d(TAG, "Started recording successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _isRecording.value = false
            release()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Optional: could update a visual indicator of voice level here
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not needed for basic functionality
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                
                // Don't restart immediately to give the system time to process results
                // We'll handle restarting after results or errors
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                
                Log.e(TAG, "Recognition error: $errorMessage ($error)")
                
                // Only restart for non-fatal errors and if still recording
                if ((error == SpeechRecognizer.ERROR_NO_MATCH || 
                     error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && 
                     _isRecording.value) {
                    
                    // Wait briefly before restarting to avoid rapid cycling
                    handler.postDelayed({
                        try {
                            if (_isRecording.value) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) // Increase results count
                                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                                    // Use longer silence timeout for better recognition
                                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
                                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                                }
                                speechRecognizer?.startListening(intent)
                                
                                // Avoid duplicate error logging for common errors
                                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                                    Log.d(TAG, "Restarted listening after error")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restarting speech recognition: ${e.message}", e)
                        }
                    }, 500) // Slightly longer delay before restart
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    if (text.isNotEmpty()) {
                        // Append to existing text instead of replacing
                        val currentText = _transcribedText.value
                        val newText = if (currentText.isEmpty()) text else "$currentText $text"
                        _transcribedText.value = newText
                        Log.d(TAG, "Updated transcribed text: '$newText'")
                        
                        // Clear partial text since we have final results
                        _partialText.value = ""
                    }
                } else {
                    Log.d(TAG, "No recognition results received")
                    
                    // If no results but we have partial text, use that as final result
                    val partialText = _partialText.value
                    if (partialText.isNotEmpty()) {
                        val currentText = _transcribedText.value
                        val newText = if (currentText.isEmpty()) partialText else "$currentText $partialText"
                        _transcribedText.value = newText
                        Log.d(TAG, "Using partial text as final: '$partialText'")
                        _partialText.value = ""
                    }
                }
                
                // Restart listening if still recording
                if (_isRecording.value) {
                    handler.postDelayed({
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                                // Use longer silence timeouts for better recognition
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                            }
                            speechRecognizer?.startListening(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restarting speech recognition: ${e.message}", e)
                        }
                    }, 300) // Short delay before restart
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    if (text.isNotEmpty()) {
                        _partialText.value = text
                        Log.d(TAG, "Partial result: '$text'")
                    } else {
                        _partialText.value = ""
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not needed for basic functionality
            }
        }
    }

    /**
     * Format a duration in seconds to a string in the format MM:SS
     * @param durationInSeconds Duration in seconds
     * @return Formatted duration string
     */
    fun formatDuration(durationInSeconds: Int): String {
        val minutes = durationInSeconds / 60
        val seconds = durationInSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun stopRecording() {
        Log.d(TAG, "stopRecording called. Current state: recording=${_isRecording.value}")
        if (!_isRecording.value) return

        try {
            speechRecognizer?.stopListening()
            
            _isRecording.value = false
            handler.removeCallbacks(updateTimer)

            Log.d(TAG, "Recording stopped. Final transcribed text: '${_transcribedText.value}'")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
        }
    }

    fun release() {
        Log.d(TAG, "Releasing resources in VoiceRecognitionManager")

        // Stop the speech recognizer if it's running
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null

        // Reset state flows
        _isRecording.value = false
        _transcribedText.value = ""
        _partialText.value = ""
        _recordingDuration.value = 0

        // Remove any pending callbacks
        handler.removeCallbacks(updateTimer)

        Log.d(TAG, "Resources released successfully")
    }
} 