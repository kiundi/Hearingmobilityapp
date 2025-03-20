package com.example.hearingmobilityapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onNavigateToEmergencyContacts: () -> Unit) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle Notifications
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Notifications", modifier = Modifier.weight(1f))
            Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Toggle Dark Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark Mode", modifier = Modifier.weight(1f))
            Switch(checked = darkModeEnabled, onCheckedChange = { darkModeEnabled = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manage Emergency Contacts
        Button(
            onClick = { onNavigateToEmergencyContacts() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Emergency Contacts")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Policy
        Text(
            text = "Privacy Policy",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openUrl(context, "https://yourprivacypolicy.com") }
                .padding(8.dp),
            color = MaterialTheme.colors.primary
        )

        // About
        Text(
            text = "About App",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showToast(context, "Public Transport App v1.0\nDeveloped for safer travel!") }
                .padding(8.dp),
            color = MaterialTheme.colors.primary
        )
    }
}

// Open a URL (Privacy Policy)
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

// Show a toast message (About App)
fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(onNavigateToEmergencyContacts = {})
}

