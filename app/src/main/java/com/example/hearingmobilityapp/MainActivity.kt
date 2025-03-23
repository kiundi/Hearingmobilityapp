package com.example.hearingmobilityapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.ui.screens.NavigationPage
import com.example.hearingmobilityapp.ui.theme.HearingmobilityappTheme
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var gtfsViewModel: GTFSViewModel
    private lateinit var sharedViewModel: SharedViewModel
    private lateinit var oneTapSignInClient: SignInClient
    private val firestore = FirebaseFirestore.getInstance()
    private var isUserLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        oneTapSignInClient = Identity.getSignInClient(applicationContext)

        // Initialize ViewModels
        gtfsViewModel = GTFSViewModel(GTFSRepository(GTFSDatabase.getDatabase(this)))
        sharedViewModel = SharedViewModel()

        setContent {
            isUserLoggedIn = remember { auth.currentUser != null }
            val startDestinationRoute = if (isUserLoggedIn) Screen.Navigation.route else "splash"

            HearingmobilityappTheme {
                val navController = rememberNavController()

                Scaffold(
                    bottomBar = {
                        if (isUserLoggedIn) {
                            BottomNavigationBar(navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestinationRoute,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(navController = navController) {
                                if (isUserLoggedIn) {
                                    navController.navigate(Screen.Navigation.route) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("signup") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            }
                        }

                        composable("signup") {
                            SignupScreen(
                                navController = navController,
                                onGoogleSignIn = { beginGoogleSignIn() },
                                onCreateAccount = { fullName, email, phone, password, setError ->
                                    createUserWithEmailAndPassword(
                                        fullName,
                                        email,
                                        phone,
                                        password,
                                        navController,
                                        setError
                                    )
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                navController = navController,
                                onLogin = { email, password, setError ->
                                    signInWithEmailAndPassword(
                                        email,
                                        password,
                                        navController,
                                        setError
                                    )
                                },
                                onGoogleSignIn = { beginGoogleSignIn() }
                            )
                        }

                        composable(Screen.Navigation.route) {
                            NavigationPage(
                                navController = navController,
                                sharedViewModel = sharedViewModel
                            )
                        }

                        composable("chatbot") {
                            ChatbotScreen(
                                gtfsViewModel = gtfsViewModel,
                                sharedViewModel = sharedViewModel,
                                onNavigateToMap = {
                                    navController.navigate(Screen.Navigation.route)
                                }
                            )
                        }

                        // Add other routes (Report, Communication, Account)
                        composable(Screen.Communication.route) {
                            CommunicationPage()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToHomeScreen(navController: NavHostController) {
        navController.navigate(Screen.Navigation.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    private val googleSignInResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val credential = oneTapSignInClient.getSignInCredentialFromIntent(result.data)
                credential.googleIdToken?.let { token ->
                    signInWithGoogle(token)
                }
            } else {
                Log.e("GoogleSignIn", "Google Sign-in failed")
                // Handle sign-in failure
            }
        }

    private fun beginGoogleSignIn() {
        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not the Android client ID.
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()

        oneTapSignInClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                val intentSenderRequest =
                    IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                googleSignInResultLauncher.launch(intentSenderRequest)
            }
            .addOnFailureListener { e ->
                Log.d("GoogleSignIn", "Couldn't start One Tap UI: ${e.localizedMessage}")
                // Fallback to standard Google Sign-In if One Tap fails
                // Implement your standard Google Sign-In flow here if needed
            }
    }

    private fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        lifecycleScope.launch {
            try {
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                user?.let { saveUserToFirestore(it, "google") }
                isUserLoggedIn = true
                // navController?.navigate(Screen.Navigation.route) { ... } // Navigation will happen in MainScreen
            } catch (e: Exception) {
                Log.e("FirebaseGoogleAuth", "Google sign-in failed", e)
                // Handle Firebase Google sign-in failure
            }
        }
    }

    private fun createUserWithEmailAndPassword(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        navController: NavHostController,
        setError: (String) -> Unit // New setError callback
    ) {
        lifecycleScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user
                user?.let { saveUserToFirestore(it, "emailPassword", fullName, phone) }
                isUserLoggedIn = true
                navigateToHomeScreen(navController)
            } catch (e: Exception) {
                Log.e("FirebaseEmailAuth", "Sign-up failed", e)
                setError(e.localizedMessage ?: "Sign-up failed.") // Call setError with the error message
                // Handle sign-up failure (e.g., display error message)
            }
        }
    }

    private fun signInWithEmailAndPassword(
        email: String,
        password: String,
        navController: NavHostController,
        setError: (String) -> Unit // New setError callback
    ) {
        lifecycleScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                isUserLoggedIn = true
                navigateToHomeScreen(navController)
            } catch (e: Exception) {
                Log.e("FirebaseEmailAuth", "Login failed", e)
                setError(e.localizedMessage ?: "Login failed.") // Call setError with the error message
                // Handle login failure (e.g., display error message)
            }
        }
    }

    private fun saveUserToFirestore(
        user: FirebaseUser,
        provider: String,
        fullName: String? = null,
        phone: String? = null
    ) {
        val userMap = hashMapOf(
            "email" to (user.email ?: ""),
            "provider" to provider
        )
        fullName?.let { userMap["fullName"] = it }
        phone?.let { userMap["phone"] = it }

        firestore.collection("users").document(user.uid)
            .set(userMap)
            .addOnSuccessListener {
                Log.d("Firestore", "User data saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user data", e)
            }
    }
}