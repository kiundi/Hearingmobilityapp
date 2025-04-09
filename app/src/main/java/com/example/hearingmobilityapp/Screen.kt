package com.example.hearingmobilityapp

import androidx.annotation.DrawableRes

sealed class Screen(val route: String, @DrawableRes val iconRes: Int, val label: String) {
    data object Navigation : Screen("navigation", R.drawable.map_icon, "Directions")
    data object Communication : Screen("communication", R.drawable.keyboard_icon, "Communication")
    data object Account : Screen("account", R.drawable.account_icon, "Account")
    data object EmergencyContacts : Screen("emergency_contacts", R.drawable.account_icon, "Emergency Contacts")
}