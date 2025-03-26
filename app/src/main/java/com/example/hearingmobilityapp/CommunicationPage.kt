package com.example.hearingmobilityapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val MAX_CHARACTERS = 500

@Composable
fun CommunicationPage(communicationViewModel: CommunicationViewModel = viewModel()) {
    var typedMessage by remember { mutableStateOf("") }
    var displayedMessage by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var showSavedMessagesScreen by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Row: Saved Messages & Favorites without clipping in a simple layout.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showSavedMessagesScreen = true }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.menu_icon),
                        contentDescription = "Saved Messages",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF007AFF)
                    )
                    Text(
                        text = "Saved",
                        fontSize = 10.sp,
                        color = Color(0xFF6C757D)
                    )
                }
            }
            IconButton(onClick = { /* Handle favorite toggle */ }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(
                            id = if (isFavorite) R.drawable.starred_icon else R.drawable.star_icon
                        ),
                        contentDescription = "Favorites",
                        modifier = Modifier.size(24.dp),
                        tint = if (isFavorite) Color(0xFFFF9500) else Color(0xFF007AFF)
                    )
                    Text(
                        text = "Favs",
                        fontSize = 10.sp,
                        color = Color(0xFF6C757D)
                    )
                }
            }
        }

        // Center: Signboard for displaying the message.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Padding adjusted to center text between top icons and bottom input.
                .padding(top = 40.dp, bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (displayedMessage.isNotEmpty()) displayedMessage else "Message appears here",
                color = if (displayedMessage.isNotEmpty()) Color.Black else Color.LightGray,
                fontSize = if (displayedMessage.isNotEmpty()) 36.sp else 48.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = Int.MAX_VALUE // Allow text to wrap indefinitely.
            )
        }

        // Bottom Row: Multi-line TextField and Microphone Button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = typedMessage,
                onValueChange = { newText ->
                    if (newText.length <= MAX_CHARACTERS) {
                        typedMessage = newText
                        displayedMessage = newText.ifEmpty { "Message appears here" }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 120.dp)
                    .padding(end = 8.dp),
                placeholder = { Text("Type message here...", color = Color.LightGray) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Start
                ),
                singleLine = false, // Allow multi-line input.
                maxLines = Int.MAX_VALUE,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        displayedMessage = typedMessage
                        typedMessage = ""
                        focusManager.clearFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = Color(0xFF6C757D),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black
                )
            )
            IconButton(onClick = { isListening = !isListening }) {
                Icon(
                    painter = painterResource(id = R.drawable.microphone_icon),
                    contentDescription = "Microphone",
                    tint = if (isListening) Color(0xFFFF9500) else Color(0xFF007AFF),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Overlay for Saved Messages Screen.
        if (showSavedMessagesScreen) {
            SavedMessagesScreen(
                viewModel = communicationViewModel,
                onClose = { showSavedMessagesScreen = false },
                onMessageSelected = { selectedMessage ->
                    // Update displayed message from saved selection.
                    displayedMessage = selectedMessage
                    showSavedMessagesScreen = false
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CommunicationPagePreview() {
    CommunicationPage()
}
