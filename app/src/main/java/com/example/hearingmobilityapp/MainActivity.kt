package com.example.hearingmobilityapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                                MainScreen()
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
                    }
                }
            }
        }
    }
}
