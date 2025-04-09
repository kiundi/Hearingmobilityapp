package com.example.hearingmobilityapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val userViewModel: UserViewModel = viewModel()
                    val currentUser by userViewModel.currentUser.collectAsState()
                    val context = LocalContext.current
                    
                    // Handle system back button to exit app when on main screen
                    val backPressedDispatcher = (context as ComponentActivity).onBackPressedDispatcher
                    val backCallback = remember {
                        object : OnBackPressedCallback(true) {
                            override fun handleOnBackPressed() {
                                val currentRoute = navController.currentBackStackEntry?.destination?.route
                                
                                when (currentRoute) {
                                    "main" -> {
                                        // When on main screen, finish the activity to exit app
                                        (context as ComponentActivity).finish()
                                    }
                                    else -> {
                                        // For other screens, allow normal back navigation
                                        if (isEnabled) {
                                            isEnabled = false
                                            backPressedDispatcher.onBackPressed()
                                            isEnabled = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    DisposableEffect(key1 = backPressedDispatcher) {
                        backPressedDispatcher.addCallback(backCallback)
                        onDispose {
                            backCallback.remove()
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(navController, userViewModel)
                        }
                        composable("signup") {
                            SignupScreen(navController, userViewModel)
                        }
                        composable("login") {
                            LoginScreen(navController, userViewModel)
                        }
                        composable("main") {
                            if (currentUser != null) {
                                MainScreen(parentNavController = navController)
                            } else {
                                // If somehow user gets to main without being logged in, redirect to login
                                navController.navigate("login") {
                                    popUpTo("main") { inclusive = true }
                                }
                            }
                        }
                        // Add other screen routes here
                        composable("account") {
                            AccountScreen(navController, userViewModel)
                        }
                        composable("emergency_contacts") {
                            EmergencyContactsScreen(navController, userViewModel)
                        }
                        composable("report_emergency") {
                            ReportEmergencyScreen(navController, userViewModel)
                        }
                    }
                }
            }
        }
    }
}
