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
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var oneTapSignInClient: SignInClient

    private var isUserLoggedIn by mutableStateOf(false)
    private var startDestinationRoute by mutableStateOf("splash")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))

        enableEdgeToEdge()

        oneTapSignInClient = Identity.getSignInClient(applicationContext)

        setContent {
            isUserLoggedIn = remember { auth.currentUser != null }
            startDestinationRoute = if (isUserLoggedIn) Screen.Navigation.route else "splash"

            HearingmobilityappTheme {
                MainScreen(startDestination = startDestinationRoute)
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

    @Composable
    fun MainScreen(startDestination: String) {
        val navController = rememberNavController()

        Scaffold(
            bottomBar = {
                if (isUserLoggedIn) {
                    @Composable
                    fun BottomNavigationBar(navController: NavHostController) {
                        val items = listOf(
                            Screen.Report,
                            Screen.Navigation,
                            Screen.Communication,
                            Screen.Account
                        )
                        // You will implement the BottomNavigationBar composable here
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("splash") {
                    SplashScreen(navController = navController) {
                        if (isUserLoggedIn) {
                            navigateToHomeScreen(navController)
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
                        onCreateAccount = { fullName, email, phone, password, setError -> // Receive setError
                            createUserWithEmailAndPassword(
                                fullName,
                                email,
                                phone,
                                password,
                                navController,
                                setError // Pass setError to the createUser function
                            )
                        }
                    )
                }
                composable("login") {
                    LoginScreen(
                        navController = navController,
                        onLogin = { email, password, setError -> // Receive setError
                            signInWithEmailAndPassword(
                                email,
                                password,
                                navController,
                                setError // Pass setError to the signIn function
                            )
                        },
                        onGoogleSignIn = { beginGoogleSignIn() }
                    )
                }

                composable(Screen.Communication.route) {
                    CommunicationPage()
                }
                composable(Screen.Navigation.route) {
                    NavigationPage(navController) // ✅ Pass navController here
                }
                composable(route = "chatbot") {
                    ChatbotScreen()
                }
            }
        }
    }
}
// Add other pages here (Report, Account)