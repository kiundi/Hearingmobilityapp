package com.example.hearingmobilityapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserData(
    val uid: String,
    val name: String,
    val email: String,
    val provider: String = "emailPassword"
)

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class UserViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        // Enable offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        firestoreSettings = settings
    }
    
    private val _currentUser = MutableStateFlow<UserData?>(null)
    val currentUser: StateFlow<UserData?> = _currentUser

    init {
        // Initialize current user state
        auth.currentUser?.let { firebaseUser ->
            viewModelScope.launch {
                loadUserData(firebaseUser)
            }
        }
    }

    private suspend fun loadUserData(firebaseUser: FirebaseUser) {
        try {
            val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
            if (userDoc.exists()) {
                _currentUser.value = UserData(
                    uid = firebaseUser.uid,
                    name = userDoc.getString("name") ?: "",
                    email = userDoc.getString("email") ?: "",
                    provider = userDoc.getString("provider") ?: "emailPassword"
                )
            } else {
                // Create a basic user profile if document doesn't exist
                val basicUserData = UserData(
                    uid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: ""
                )
                _currentUser.value = basicUserData
                
                // Try to save this basic profile to Firestore
                try {
                    db.collection("users").document(firebaseUser.uid)
                        .set(basicUserData)
                } catch (e: Exception) {
                    Log.w("UserViewModel", "Couldn't save basic profile while offline", e)
                }
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error loading user data (Ask Gemini)", e)
            
            // Even if we can't load from Firestore, create a basic user from auth
            auth.currentUser?.let { user ->
                _currentUser.value = UserData(
                    uid = user.uid,
                    name = user.displayName ?: "",
                    email = user.email ?: ""
                )
            }
        }
    }

    suspend fun signUp(name: String, email: String, password: String): AuthResult {
        return try {
            // Add safety check for empty credentials
            if (email.isBlank() || password.isBlank() || name.isBlank()) {
                return AuthResult.Error("Please fill in all fields")
            }

            // Check if email is already in use
            try {
                val result = auth.fetchSignInMethodsForEmail(email).await()
                if (result.signInMethods?.isNotEmpty() == true) {
                    return AuthResult.Error("This email address is already registered. Please try signing in instead.")
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error checking email existence", e)
            }

            // Attempt to create the user
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let { firebaseUser ->
                    val userData = UserData(
                        uid = firebaseUser.uid,
                        name = name,
                        email = email
                    )
                    // Store user data in Firestore
                    db.collection("users").document(firebaseUser.uid)
                        .set(userData).await()
                    
                    _currentUser.value = userData
                }
                AuthResult.Success
            } catch (e: FirebaseAuthUserCollisionException) {
                Log.e("UserViewModel", "Email collision during sign up", e)
                AuthResult.Error("This email address is already in use. Please try a different email or sign in.")
            } catch (e: Exception) {
                Log.e("UserViewModel", "Network error during sign up", e)
                AuthResult.Error("Sign-up failed. Please check your network connection and try again.")
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Sign up error", e)
            AuthResult.Error(e.message ?: "Sign up failed")
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            if (email.isBlank() || password.isBlank()) {
                return AuthResult.Error("Please fill in all fields")
            }

            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { firebaseUser ->
                loadUserData(firebaseUser)
            }
            AuthResult.Success
        } catch (e: Exception) {
            Log.e("UserViewModel", "Sign in error", e)
            AuthResult.Error("Sign in failed. Please check your internet connection and try again.")
        }
    }
    
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.let { firebaseUser ->
                val userData = UserData(
                    uid = firebaseUser.uid,
                    name = "Guest User",
                    email = "",
                    provider = "anonymous"
                )
                _currentUser.value = userData
                
                // Try to store user data in Firestore
                try {
                    db.collection("users").document(firebaseUser.uid)
                        .set(userData)
                } catch (e: Exception) {
                    Log.w("UserViewModel", "Couldn't save anonymous user while offline", e)
                }
            }
            AuthResult.Success
        } catch (e: Exception) {
            Log.e("UserViewModel", "Anonymous sign in error", e)
            AuthResult.Error("Anonymous sign in failed. Please try again later.")
        }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }
}
