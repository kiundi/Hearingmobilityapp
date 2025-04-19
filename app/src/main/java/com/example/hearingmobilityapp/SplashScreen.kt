package com.example.hearingmobilityapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavHostController,
    userViewModel: UserViewModel = viewModel()
) {
    val currentUser = userViewModel.currentUser.collectAsState().value
    val authInitialized = remember { mutableStateOf(false) }
    
    // Launch a separate effect to check if user is signed in and initialize auth
    LaunchedEffect(Unit) {
        // Auth state is automatically checked in the UserViewModel's init block
        
        // Wait a moment to ensure Auth has time to fully initialize
        delay(1000)
        authInitialized.value = true
    }
    
    // Only navigate after auth is initialized and splash screen delay has passed
    LaunchedEffect(authInitialized.value, currentUser) {
        if (authInitialized.value) {
            delay(1000) // Additional delay to show the splash screen (2 seconds total)
            
            if (currentUser != null) {
                // User is logged in, go to main screen
                navController.navigate("main") {
                    popUpTo(0) { inclusive = true }
                }
            } else {
                // No user logged in, go to signup screen
                navController.navigate("signup") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "DIGITAL MATATU",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_bus),
                contentDescription = "App Logo",
                modifier = Modifier.height(160.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Hearing Mobility App",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6C757D)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    val navController = rememberNavController()
    SplashScreen(navController = navController)
}
