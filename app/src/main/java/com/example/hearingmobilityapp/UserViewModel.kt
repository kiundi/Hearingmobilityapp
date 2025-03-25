package com.example.hearingmobilityapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
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
    private val db = FirebaseFirestore.getInstance()
    
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
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error loading user data", e)
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
            AuthResult.Error(e.message ?: "Sign in failed")
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
