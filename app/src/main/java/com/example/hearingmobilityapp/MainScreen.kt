package com.example.hearingmobilityapp

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen(
    parentNavController: NavHostController // Add this parameter to receive the main NavController
) {
    // Create a dedicated NavController for bottom navigation.
    val bottomNavController = rememberNavController()

    // Define the bottom navigation items from your Screen sealed class.
    val bottomNavItems = listOf(
        Screen.Navigation,
        Screen.Communication,
        Screen.Account
    )

    // Create shared ViewModels at the top level to share across screens
    val sharedViewModel: SharedViewModel = viewModel()
    val communicationViewModel: CommunicationViewModel = viewModel()
    val userViewModel: UserViewModel = viewModel()
    
    // Get application context for GTFSViewModel
    val appContext = LocalContext.current.applicationContext as Application
    
    Scaffold(
        bottomBar = { BottomNavBar(navController = bottomNavController, items = bottomNavItems) }
    ) { innerPadding ->
        // The nested NavHost ensures that the bottom nav bar remains visible.
        NavHost(
            navController = bottomNavController,
            startDestination = Screen.Communication.route, // Communication is the default page.
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Navigation.route) {
                NavigationScreen(
                    navController = bottomNavController,
                    viewModel = communicationViewModel,
                    sharedViewModel = sharedViewModel
                )
            }
            
            // Add composables for other bottom nav destinations
            composable(Screen.Communication.route) {
                CommunicationPage(
                    viewModel = communicationViewModel
                )
            }
            
            // Use the parentNavController for the AccountScreen so it can navigate to routes
            // defined in the main NavHost
            composable(Screen.Account.route) {
                AccountScreen(
                    navController = parentNavController,
                    userViewModel = userViewModel
                )
            }
            
            // Add chatbot screen
            composable("chatbot") {
                ChatbotScreen(
                    navController = bottomNavController,
                    gtfsViewModel = viewModel(
                        factory = GTFSViewModel.Factory(appContext)
                    ),
                    communicationViewModel = communicationViewModel
                )
            }
            
            // Add trip details screen
            composable("TripDetailsScreen") {
                TripDetailsScreen(
                    navController = bottomNavController,
                    sharedViewModel = sharedViewModel,
                    gtfsViewModel = viewModel(
                        factory = GTFSViewModel.Factory(appContext)
                    ),
                    communicationViewModel = communicationViewModel
                )
            }
            
            // Add real-time transit screen
            composable("realTimeTransit") {
                RealTimeTransitScreen(
                    navController = bottomNavController
                )
            }
        }
    }
}
