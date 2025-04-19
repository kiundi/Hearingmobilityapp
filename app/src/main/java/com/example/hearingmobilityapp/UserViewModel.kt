package com.example.hearingmobilityapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearingmobilityapp.auth.AuthService
import com.example.hearingmobilityapp.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val isPrimary: Boolean
)

data class UserData(
    val uid: String,
    val name: String,
    val email: String,
    val provider: String = "emailPassword",
    val emergencyContacts: List<EmergencyContact> = emptyList()
)

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class UserViewModel(application: Application) : AndroidViewModel(application) {
    // Replace Firebase Auth with our custom AuthService
    private val authService = AuthService(application.applicationContext)
    
    private val _currentUser = MutableStateFlow<UserData?>(null)
    val currentUser: StateFlow<UserData?> = _currentUser

    init {
        // Observe the current user from AuthService
        viewModelScope.launch {
            authService.currentUser.collectLatest { user ->
                user?.let {
                    _currentUser.value = UserData(
                        uid = it.uid,
                        name = it.name ?: "",
                        email = it.email,
                        provider = "emailPassword",
                        emergencyContacts = emptyList() // We'll need to load these separately
                    )
                    // We should load emergency contacts here once we migrate that to SQLite too
                } ?: run {
                    _currentUser.value = null
                }
            }
        }
        
        // Check if there's a currently authenticated user
        checkForExistingUser()
    }
    
    // Public method to explicitly check authentication state
    fun checkAuthState() {
        // The authService's currentUser flow will handle this automatically
    }
    
    private fun checkForExistingUser() {
        // The AuthService already checks for existing users in its init block
        // and updates the currentUser flow accordingly
    }

    suspend fun signUp(name: String, email: String, password: String): AuthResult {
        return try {
            // Add safety check for empty credentials
            if (email.isBlank() || password.isBlank() || name.isBlank()) {
                return AuthResult.Error("Please fill in all fields")
            }

            // Call our AuthService to sign up
            val result = authService.signUp(email, password, name)
            
            if (result.isSuccess) {
                Log.d("UserViewModel", "Successfully signed up user $email")
                AuthResult.Success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Sign-up failed"
                Log.e("UserViewModel", "Sign up error: $errorMsg")
                AuthResult.Error(errorMsg)
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

            val result = authService.signIn(email, password)
            
            if (result.isSuccess) {
                Log.d("UserViewModel", "Successfully signed in user $email")
                AuthResult.Success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Sign-in failed"
                Log.e("UserViewModel", "Sign in error: $errorMsg")
                AuthResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Sign in error", e)
            AuthResult.Error("Sign in failed. Please check your credentials and try again.")
        }
    }
    
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = authService.signInAnonymously()
            
            if (result.isSuccess) {
                Log.d("UserViewModel", "Successfully signed in anonymously")
                result.getOrNull()?.let { user ->
                    _currentUser.value = UserData(
                        uid = user.uid,
                        name = user.name ?: "Guest User",
                        email = user.email,
                        provider = "anonymous",
                        emergencyContacts = emptyList()
                    )
                }
                AuthResult.Success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Anonymous sign-in failed"
                Log.e("UserViewModel", "Anonymous sign-in error: $errorMsg")
                AuthResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Anonymous sign-in error", e)
            AuthResult.Error("Anonymous sign-in failed. Please try again.")
        }
    }

    fun signOut() {
        authService.signOut()
        // The _currentUser will be updated through the flow collection in init
    }

    fun isUserSignedIn(): Boolean {
        return _currentUser.value != null
    }

    suspend fun updateEmergencyContacts(contacts: List<EmergencyContact>) {
        // This would need to be migrated to SQLite as well
        // For now, just update the in-memory state
        try {
            val userData = _currentUser.value ?: return
            _currentUser.value = userData.copy(emergencyContacts = contacts)
            
            // TODO: Save emergency contacts to SQLite database
            Log.d("UserViewModel", "Emergency contacts updated in memory, database update not implemented yet")
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error updating emergency contacts", e)
        }
    }
    
    suspend fun addEmergencyContact(contact: EmergencyContact) {
        try {
            val userData = _currentUser.value ?: return
            val currentContacts = userData.emergencyContacts.toMutableList()
            currentContacts.add(contact)
            
            updateEmergencyContacts(currentContacts)
            Log.d("UserViewModel", "Emergency contact added: ${contact.name}")
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error adding emergency contact", e)
        }
    }
    
    suspend fun removeEmergencyContact(contact: EmergencyContact) {
        try {
            val userData = _currentUser.value ?: return
            val currentContacts = userData.emergencyContacts.toMutableList()
            currentContacts.remove(contact)
            
            updateEmergencyContacts(currentContacts)
            Log.d("UserViewModel", "Emergency contact removed: ${contact.name}")
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error removing emergency contact", e)
        }
    }
    
    // Other methods related to user management can be migrated similarly
}
