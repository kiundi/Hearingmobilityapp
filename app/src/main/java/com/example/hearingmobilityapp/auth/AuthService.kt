package com.example.hearingmobilityapp.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.hearingmobilityapp.database.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class AuthService(private val context: Context) {
    companion object {
        private const val TAG = "AuthService"
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
    }

    private val dbHelper = DatabaseHelper(context)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Current user state
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser
    
    init {
        // Check if user is already logged in
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val email = sharedPreferences.getString(KEY_USER_EMAIL, null)
        
        if (userId != null && email != null) {
            _currentUser.value = User(userId, email, null)
        }
    }
    
    suspend fun signUp(email: String, password: String, name: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if user already exists
                val existingUser = dbHelper.getUserByEmail(email)
                if (existingUser != null) {
                    return@withContext Result.failure(Exception("User with this email already exists"))
                }
                
                // Hash the password
                val (passwordHash, salt) = hashPassword(password)
                
                // Register user in the database
                val userId = dbHelper.registerUser(email, passwordHash, name)
                
                // Create user object
                val user = User(userId, email, name)
                
                // Save login session
                saveUserSession(userId, email)
                
                // Update current user
                _currentUser.value = user
                
                Result.success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Error signing up: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun signIn(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // Get user from database
                val userCredentials = dbHelper.getUserByEmail(email)
                    ?: return@withContext Result.failure(Exception("Invalid email or password"))
                
                val (userId, storedPasswordHash) = userCredentials
                
                // Validate password
                if (!verifyPassword(password, storedPasswordHash)) {
                    return@withContext Result.failure(Exception("Invalid email or password"))
                }
                
                // Get user details
                val userDetails = dbHelper.getUserById(userId)
                    ?: return@withContext Result.failure(Exception("User details not found"))
                
                // Create user object
                val user = User(
                    userId,
                    userDetails["email"] ?: "",
                    userDetails["name"]
                )
                
                // Save login session
                saveUserSession(userId, email)
                
                // Update current user
                _currentUser.value = user
                
                Result.success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Error signing in: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun signInAnonymously(): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // Generate a unique anonymous ID
                val anonymousId = "anon_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}"
                val anonymousEmail = "guest@anonymous.user"
                
                // Create anonymous user object
                val user = User(anonymousId, anonymousEmail, "Guest User")
                
                // Save anonymous session
                saveUserSession(anonymousId, anonymousEmail)
                
                // Update current user
                _currentUser.value = user
                
                Log.d(TAG, "Anonymous sign-in successful with ID: $anonymousId")
                Result.success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Error signing in anonymously: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    fun signOut() {
        // Clear session
        sharedPreferences.edit().clear().apply()
        
        // Update current user
        _currentUser.value = null
    }
    
    private fun saveUserSession(userId: String, email: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }
    
    private fun hashPassword(password: String): Pair<String, String> {
        // Generate a random salt
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val saltStr = Base64.getEncoder().encodeToString(salt)
        
        // Hash the password with the salt
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hashedPassword = md.digest(password.toByteArray())
        val hashedPasswordStr = Base64.getEncoder().encodeToString(hashedPassword)
        
        // Return the hashed password and salt
        return Pair("$hashedPasswordStr:$saltStr", saltStr)
    }
    
    private fun verifyPassword(password: String, storedHash: String): Boolean {
        try {
            // Extract the hash and salt
            val parts = storedHash.split(":")
            if (parts.size != 2) return false
            
            val hashStr = parts[0]
            val saltStr = parts[1]
            
            // Decode the salt
            val salt = Base64.getDecoder().decode(saltStr)
            
            // Hash the provided password with the stored salt
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            val hashedPassword = md.digest(password.toByteArray())
            val hashedPasswordStr = Base64.getEncoder().encodeToString(hashedPassword)
            
            // Compare the hashes
            return hashStr == hashedPasswordStr
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying password: ${e.message}", e)
            return false
        }
    }
}

data class User(
    val uid: String,
    val email: String,
    val name: String? = null
) 