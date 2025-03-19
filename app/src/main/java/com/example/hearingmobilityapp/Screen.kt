package com.example.hearingmobilityapp

import androidx.annotation.DrawableRes

sealed class Screen(val route: String, @DrawableRes val iconRes: Int, val label: String) {
    data object Report : Screen("report", R.drawable.report_icon, "Report")
    data object Navigation : Screen("navigation", R.drawable.map_icon, "Directions")
    data object Communication : Screen("communication", R.drawable.keyboard_icon, "Communication")
    data object Account : Screen("account", R.drawable.account_icon, "Account")
    data object Login : Screen("login", -1, "Login")
    data object Signup : Screen("signup", -1, "Sign Up")
}