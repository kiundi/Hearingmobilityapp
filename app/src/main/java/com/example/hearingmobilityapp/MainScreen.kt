package com.example.hearingmobilityapp

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen() {
    // Create a dedicated NavController for bottom navigation.
    val bottomNavController = rememberNavController()

    // Define the bottom navigation items from your Screen sealed class.
    val bottomNavItems = listOf(
        Screen.Navigation,
        Screen.Communication,
        Screen.Account
    )

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
                val sharedViewModel: CommunicationViewModel = viewModel()
                NavigationScreen(navController = bottomNavController, sharedViewModel = sharedViewModel)}

            composable(Screen.Communication.route) { CommunicationPage() }
            composable(Screen.Account.route) {AccountScreen(navController = rememberNavController()) }
        }
    }
}
