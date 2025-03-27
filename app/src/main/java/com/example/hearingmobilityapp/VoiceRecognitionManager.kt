package com.example.hearingmobilityapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

/**
 * Manager class for handling voice recognition using Vosk
 */
class VoiceRecognitionManager(private val context: Context) : RecognitionListener {
    companion object {
        private const val TAG = "VoiceRecognitionManager"
        private const val SAMPLE_RATE = 16000
    }

    // Speech recognition service
    private var speechService: SpeechService? = null
    private var model: Model? = null
    
    // Model manager
    private val modelManager = VoskModelManager(context)
    
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
    
    // Model initialization state
    private val _isModelInitialized = MutableStateFlow(false)
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
     * Initialize the speech recognition model
     * @return true if initialization was successful, false otherwise
     */
    suspend fun initModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (model != null) {
                _isModelInitialized.value = true
                return@withContext true
            }
            
            // Ensure model is available
            val modelAvailable = modelManager.ensureModelAvailable()
            if (!modelAvailable) {
                Log.e(TAG, "Failed to ensure model availability - model not available")
                _isModelInitialized.value = false
                return@withContext false
            }
            
            // Get the model path and verify it
            val modelPath = File(context.filesDir, "model")
            if (!verifyModelDirectory(modelPath)) {
                Log.e(TAG, "Model directory verification failed")
                // Try downloading the model again
                val downloadSuccess = modelManager.downloadModel()
                if (!downloadSuccess || !verifyModelDirectory(modelPath)) {
                    Log.e(TAG, "Model download failed or still not valid after download")
                    _isModelInitialized.value = false
                    return@withContext false
                }
            }
            
            // Initialize model
            try {
                Log.d(TAG, "Attempting to create Vosk model from: ${modelPath.absolutePath}")
                model = Model(modelPath.absolutePath)
                _isModelInitialized.value = true
                Log.d(TAG, "Successfully created Vosk model")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Vosk model object: ${e.message}", e)
                _isModelInitialized.value = false
                return@withContext false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize model: ${e.message}", e)
            _isModelInitialized.value = false
            return@withContext false
        }
    }
    
    /**
     * Verify that the model directory has the required structure and files
     */
    private fun verifyModelDirectory(modelDir: File): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) {
            Log.e(TAG, "Model directory does not exist or is not a directory: ${modelDir.absolutePath}")
            return false
        }
        
        // Log the full directory contents
        Log.d(TAG, "Checking model directory: ${modelDir.absolutePath}")
        modelDir.listFiles()?.forEach { file ->
            Log.d(TAG, "Found file: ${file.name}, isDir=${file.isDirectory}, size=${file.length()}")
            if (file.isDirectory) {
                file.listFiles()?.forEach { subFile ->
                    Log.d(TAG, "  - Subfile: ${subFile.name}, isDir=${subFile.isDirectory}, size=${subFile.length()}")
                }
            }
        }
        
        // Vosk requires specific files to be present
        val requiredFiles = arrayOf("am", "conf", "graph", "ivector")
        for (fileName in requiredFiles) {
            val file = File(modelDir, fileName)
            if (!file.exists()) {
                Log.e(TAG, "Required model file missing: $fileName")
                return false
            }
        }
        
        return true
    }

    /**
     * Start recording and recognizing speech
     */
    fun startRecording() {
        Log.d(TAG, "startRecording called. Current state: recording=${_isRecording.value}, model initialized=${_isModelInitialized.value}")
        if (_isRecording.value || !_isModelInitialized.value) {
            Log.d(TAG, "Cannot start recording: recording=${_isRecording.value}, model initialized=${_isModelInitialized.value}")
            return
        }
        
        try {
            // Create recognizer
            Log.d(TAG, "Creating recognizer with model at ${context.filesDir}/model")
            val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            
            // Start speech service
            speechService = SpeechService(recognizer, SAMPLE_RATE.toFloat())
            speechService?.startListening(this)
            
            // Reset values
            _transcribedText.value = ""
            _partialText.value = ""
            durationInSeconds = 0
            _recordingDuration.value = 0
            
            // Update recording state
            _isRecording.value = true
            
            // Start timer
            handler.post(updateTimer)
            
            Log.d(TAG, "Started recording successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
        }
    }

    /**
     * Stop recording and finalize transcription
     */
    fun stopRecording() {
        Log.d(TAG, "stopRecording called. Current state: recording=${_isRecording.value}")
        if (!_isRecording.value) {
            Log.d(TAG, "Not recording, nothing to stop")
            return
        }
        
        try {
            // Stop speech service
            speechService?.stop()
            speechService = null
            
            // Update recording state
            _isRecording.value = false
            
            // Stop timer
            handler.removeCallbacks(updateTimer)
            
            Log.d(TAG, "Stopped recording. Final transcription: ${_transcribedText.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
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

    // RecognitionListener implementation
    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                // Extract partial text from JSON response
                val jsonResult = org.json.JSONObject(it)
                val partialText = jsonResult.optString("partial", "")
                if (partialText.isNotEmpty()) {
                    _partialText.value = partialText
                } else {
                    _partialText.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing partial result: ${e.message}")
            }
        } ?: run {
            _partialText.value = ""
        }
    }

    override fun onResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                // Extract final text from JSON response
                val jsonResult = org.json.JSONObject(it)
                val text = jsonResult.optString("text", "")
                if (text.isNotEmpty()) {
                    _transcribedText.value = text
                    _partialText.value = ""
                } else {
                    _transcribedText.value = ""
                    _partialText.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing result: ${e.message}")
            }
        } ?: run {
            _transcribedText.value = ""
            _partialText.value = ""
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                // Extract final text from JSON response
                val jsonResult = org.json.JSONObject(it)
                val text = jsonResult.optString("text", "")
                if (text.isNotEmpty()) {
                    _transcribedText.value = text
                } else {
                    _transcribedText.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing final result: ${e.message}")
            }
        } ?: run {
            _transcribedText.value = ""
        }
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognition error: ${exception?.message}", exception)
    }

    override fun onTimeout() {
        Log.d(TAG, "Recognition timeout")
    }
}
