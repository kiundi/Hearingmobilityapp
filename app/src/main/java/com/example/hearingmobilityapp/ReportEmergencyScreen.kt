package com.example.hearingmobilityapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmergencyScreen(
    navController: NavHostController,
    userViewModel: UserViewModel = viewModel()
) {
    val currentUser by userViewModel.currentUser.collectAsState()
    val context = LocalContext.current
    
    // Emergency message templates
    val emergencyMessages = listOf(
        "I need help - I am feeling unsafe on this matatu",
        "I missed my stop and I'm lost. Please help me find my way",
        "My matatu broke down and I need assistance",
        "Someone is harassing me",
        "I've been involved in an accident and need immediate help",
        "I can't communicate with the conductor",
        "My phone battery is low, I need you to track my location",
        "I'm stranded without fare, please help me get home"
    )
    
    var selectedMessageIndex by remember { mutableStateOf<Int?>(null) }
    var customMessage by remember { mutableStateOf("") }
    var showContactSelection by remember { mutableStateOf(false) }
    var finalMessage by remember { mutableStateOf("") }
    
    // Selected contact and messaging states
    var selectedContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var showMessagingAppSelection by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Report Emergency",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF007AFF)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Emergency Message",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.Start)
            )
            
            Text(
                text = "Choose a pre-written message or write your own:",
                fontSize = 16.sp,
                color = Color(0xFF6C757D),
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.Start)
            )
            
            // Pre-defined emergency messages
            emergencyMessages.forEachIndexed { index, message ->
                EmergencyMessageCard(
                    message = message,
                    isSelected = selectedMessageIndex == index,
                    onClick = {
                        selectedMessageIndex = index
                        customMessage = ""
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Custom message option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = if (selectedMessageIndex == null && customMessage.isNotEmpty()) 
                        Color(0xFF007AFF) else Color.LightGray
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Other (Type your own message)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { 
                            customMessage = it
                            selectedMessageIndex = null
                        },
                        label = { Text("Type your emergency message") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = Color.LightGray,
                            focusedLabelColor = Color(0xFF007AFF),
                            unfocusedLabelColor = Color(0xFF6C757D)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    finalMessage = when {
                        selectedMessageIndex != null -> emergencyMessages[selectedMessageIndex!!]
                        customMessage.isNotEmpty() -> customMessage
                        else -> return@Button
                    }
                    showContactSelection = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = selectedMessageIndex != null || customMessage.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    disabledContainerColor = Color(0xFFADD8E6)
                )
            ) {
                Text(
                    "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // Contact selection dialog
    if (showContactSelection) {
        val emergencyContacts = currentUser?.emergencyContacts ?: emptyList()
        
        if (emergencyContacts.isEmpty()) {
            AlertDialog(
                onDismissRequest = { showContactSelection = false },
                title = { Text("No Emergency Contacts", fontWeight = FontWeight.Bold) },
                text = { Text("You haven't added any emergency contacts yet. Please add emergency contacts first.") },
                confirmButton = {
                    TextButton(onClick = { 
                        showContactSelection = false
                        navController.navigate("emergency_contacts")
                    }) {
                        Text("Add Contacts", color = Color(0xFF007AFF))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showContactSelection = false }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showContactSelection = false },
                title = { Text("Select Contact") },
                text = { 
                    Column {
                        Text("Who would you like to send this message to?")
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        emergencyContacts.forEach { contact ->
                            EmergencyContactSelectionItem(
                                contact = contact,
                                onClick = {
                                    selectedContact = contact
                                    showContactSelection = false
                                    showMessagingAppSelection = true
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showContactSelection = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    
    // Messaging app selection dialog
    if (showMessagingAppSelection && selectedContact != null) {
        val messageToSend = finalMessage
        val phoneNumber = selectedContact!!.phoneNumber
        
        AlertDialog(
            onDismissRequest = { showMessagingAppSelection = false },
            title = { Text("Send via") },
            text = { 
                Column {
                    Text("Choose how to send your emergency message")
                    Spacer(modifier = Modifier.height(16.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showMessagingAppSelection = false
                    // Open SMS app with pre-filled message
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumber")
                        putExtra("sms_body", messageToSend)
                    }
                    context.startActivity(smsIntent)
                }) {
                    Text("SMS/Messages")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showMessagingAppSelection = false
                    // Open WhatsApp with pre-filled message
                    try {
                        // Format phone number (remove any spaces, dashes, etc.)
                        val formattedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                        
                        // WhatsApp deep link
                        val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=${Uri.encode(messageToSend)}")
                            `package` = "com.whatsapp"
                        }
                        context.startActivity(whatsappIntent)
                    } catch (e: Exception) {
                        // WhatsApp not installed or other error
                        val toast = android.widget.Toast.makeText(
                            context, 
                            "WhatsApp not installed or unavailable", 
                            android.widget.Toast.LENGTH_SHORT
                        )
                        toast.show()
                        
                        // Fall back to SMS
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:$phoneNumber")
                            putExtra("sms_body", messageToSend)
                        }
                        context.startActivity(smsIntent)
                    }
                }) {
                    Text("WhatsApp")
                }
            }
        )
    }
}

@Composable
fun EmergencyMessageCard(
    message: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) Color(0xFF007AFF) else Color.LightGray
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(text = message)
        }
    }
}

@Composable
fun EmergencyContactSelectionItem(
    contact: EmergencyContact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = contact.phoneNumber,
                fontSize = 14.sp,
                color = Color.Gray
            )
            if (contact.isPrimary) {
                Text(
                    text = "Primary Contact",
                    fontSize = 12.sp,
                    color = Color(0xFF007AFF)
                )
            }
        }
    }
} 