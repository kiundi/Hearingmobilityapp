package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

/**
 * Manager class for handling Vosk model files
 */
class VoskModelManager(private val context: Context) {
    companion object {
        private const val TAG = "VoskModelManager"
        private const val MODEL_NAME = "model-en-us"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }

    /**
     * Check if the model exists in the device's internal storage
     * @return true if the model exists, false otherwise
     */
    fun isModelAvailable(): Boolean {
        val modelDir = File(context.filesDir, "model")
        val hasFiles = modelDir.exists() && modelDir.isDirectory && modelDir.list()?.isNotEmpty() == true
        
        if (hasFiles) {
            Log.d(TAG, "Model directory exists at ${modelDir.absolutePath} with ${modelDir.list()?.size ?: 0} files")
            // List the files to help debug
            modelDir.list()?.forEach { fileName ->
                Log.d(TAG, "Model directory contains: $fileName")
            }
        } else {
            Log.d(TAG, "Model directory does not exist or is empty at ${modelDir.absolutePath}")
        }
        
        return hasFiles
    }

    /**
     * Extract the model from assets
     * @return true if extraction was successful, false otherwise
     */
    suspend fun extractModelFromAssets(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // Create model directory if it doesn't exist
            val modelDir = File(context.filesDir, "model")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            // Extract model from assets with callbacks
            StorageService.unpack(
                context, 
                MODEL_NAME, 
                "model",
                { 
                    Log.d(TAG, "Model extraction from assets completed successfully")
                    // Verify the extraction worked
                    val success = isModelAvailable() && verifyModelStructure(modelDir)
                    continuation.resume(success)
                }, // Success callback
                { exception -> 
                    Log.e(TAG, "Failed to extract model from assets: $exception")
                    continuation.resume(false) 
                } // Error callback
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract model from assets: ${e.message}")
            continuation.resume(false)
        }
    }

    /**
     * Verifies the model structure has the required files for Vosk to function
     */
    private fun verifyModelStructure(modelDir: File): Boolean {
        try {
            // Check if the model directory contains the required files for Vosk
            // Vosk requires specific files like am, conf, graph, etc.
            val requiredFiles = arrayOf("am", "conf", "graph", "ivector")
            
            // Sometimes the model is extracted within a subdirectory with the model name
            // Let's check both the model directory and any immediate subdirectories
            val possibleModelDirs = listOf(modelDir) + 
                modelDir.listFiles()?.filter { it.isDirectory }?.toList().orEmpty()
            
            for (dir in possibleModelDirs) {
                Log.d(TAG, "Checking potential model directory: ${dir.absolutePath}")
                // Log all contents for debugging
                dir.listFiles()?.forEach { file ->
                    Log.d(TAG, "Found file in ${dir.name}: ${file.name}")
                }
                
                // Check if all required files exist
                val allFilesExist = requiredFiles.all { reqFile ->
                    val fileExists = dir.listFiles()?.any { it.name == reqFile } == true
                    if (!fileExists) {
                        Log.d(TAG, "Required file missing in ${dir.name}: $reqFile")
                    }
                    fileExists
                }
                
                if (allFilesExist) {
                    // If we found the files in a subdirectory but not directly in the model dir,
                    // we need to move them up to the right location
                    if (dir != modelDir) {
                        Log.d(TAG, "Found model files in subdirectory, moving to correct location")
                        // Move all files from the subdirectory to the model directory
                        dir.listFiles()?.forEach { file ->
                            val destFile = File(modelDir, file.name)
                            if (destFile.exists()) {
                                destFile.delete()
                            }
                            file.copyTo(destFile, overwrite = true)
                        }
                    }
                    Log.d(TAG, "Model structure verification successful")
                    return true
                }
            }
            
            Log.e(TAG, "Model structure verification failed - required files not found")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying model structure: ${e.message}", e)
            return false
        }
    }

    /**
     * Download the model from the internet and extract it
     * @return true if download and extraction were successful, false otherwise
     */
    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting model download from: $MODEL_URL")
            
            // Clear any existing model directory
            val modelDir = File(context.filesDir, "model")
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            modelDir.mkdirs()
            
            // Create temporary file for downloaded zip
            val tempFile = File(context.cacheDir, "model.zip")
            if (tempFile.exists()) {
                tempFile.delete()
            }
            
            // Download the model
            val url = URL(MODEL_URL)
            val connection = url.openConnection()
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000 // 30 seconds
            connection.connect()
            
            // Save to temporary file
            Log.d(TAG, "Downloading model file...")
            val inputStream = connection.getInputStream()
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(8192) // Larger buffer for faster download
            var bytesRead: Int
            var totalBytesRead = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
            }
            
            outputStream.close()
            inputStream.close()
            Log.d(TAG, "Model downloaded successfully: $totalBytesRead bytes")
            
            // Extract the zip file
            Log.d(TAG, "Extracting model files...")
            val zipInputStream = ZipInputStream(tempFile.inputStream())
            var zipEntry = zipInputStream.nextEntry
            var entriesExtracted = 0
            
            // Keep track of all top-level directories we extract
            val extractedTopDirs = mutableSetOf<String>()
            
            while (zipEntry != null) {
                val entryName = zipEntry.name
                val firstPathComponent = entryName.split("/").firstOrNull() ?: ""
                if (firstPathComponent.isNotEmpty()) {
                    extractedTopDirs.add(firstPathComponent)
                }
                
                // The Vosk model zip typically has a root directory with the model name
                // We want to extract directly to our model directory, so we need to strip that prefix
                val targetPath = if (entryName.startsWith("vosk-model")) {
                    // Remove the first directory component
                    entryName.substringAfter('/')
                } else {
                    entryName
                }
                
                if (targetPath.isNotEmpty()) {
                    val entryFile = File(modelDir, targetPath)
                    
                    // Create directories if needed
                    if (zipEntry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        // Create parent directories if needed
                        entryFile.parentFile?.mkdirs()
                        
                        // Extract file
                        val entryOutputStream = FileOutputStream(entryFile)
                        val entryBuffer = ByteArray(8192)
                        var entryBytesRead: Int
                        
                        while (zipInputStream.read(entryBuffer).also { entryBytesRead = it } != -1) {
                            entryOutputStream.write(entryBuffer, 0, entryBytesRead)
                        }
                        
                        entryOutputStream.close()
                        entriesExtracted++
                    }
                }
                
                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }
            
            zipInputStream.close()
            Log.d(TAG, "Extracted $entriesExtracted files from zip")
            
            // If all files are in a subdirectory, move them up
            // This handles models that have a directory structure like "vosk-model-small-en-us-0.15/am" etc.
            val subdirs = modelDir.listFiles()?.filter { it.isDirectory }?.toList().orEmpty()
            if (subdirs.size == 1 && modelDir.listFiles()?.size == 1) {
                val subdir = subdirs[0]
                Log.d(TAG, "Model files are in a subdirectory: ${subdir.name}, moving them up")
                
                // Move all files from the subdirectory to the model directory
                subdir.listFiles()?.forEach { file ->
                    val destFile = File(modelDir, file.name)
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    Log.d(TAG, "Moving ${file.name} to model directory")
                    file.copyTo(destFile, overwrite = true)
                }
            }
            
            // Delete temporary file
            tempFile.delete()
            
            // Verify the model structure
            val isValid = verifyModelStructure(modelDir)
            if (!isValid) {
                Log.e(TAG, "Model files were extracted but structure verification failed")
                return@withContext false
            }
            
            Log.d(TAG, "Model download and extraction completed successfully")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and extract model: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Ensure the model is available, either by extracting from assets or downloading
     * @return true if the model is available, false otherwise
     */
    suspend fun ensureModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        // Check if model already exists
        if (isModelAvailable()) {
            val modelDir = File(context.filesDir, "model")
            val isValid = verifyModelStructure(modelDir)
            if (isValid) {
                Log.d(TAG, "Model already exists and is valid")
                return@withContext true
            } else {
                Log.d(TAG, "Model exists but structure is invalid, will try to recreate")
            }
        }
        
        // Try to extract from assets
        Log.d(TAG, "Attempting to extract model from assets")
        if (extractModelFromAssets()) {
            Log.d(TAG, "Model extracted from assets successfully")
            return@withContext true
        }
        
        // If extraction fails, try to download
        Log.d(TAG, "Asset extraction failed, attempting to download model")
        return@withContext downloadModel()
    }
}
