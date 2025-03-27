package com.example.hearingmobilityapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectTapGestures
import android.widget.Toast

private const val MAX_CHARACTERS = 500

@Composable
fun CommunicationPage(viewModel: CommunicationViewModel = viewModel()) {
    var typedMessage by remember { mutableStateOf("") }
    var displayedMessage by remember { mutableStateOf("") }
    val isListening by viewModel.isListening.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val partialTranscription by viewModel.partialTranscription.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var showSavedMessagesScreen by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    
    // State for delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Get notification states from ViewModel
    val showAddedToFavoritesMessage by viewModel.showAddedToFavoritesMessage.collectAsState()
    val showRemovedFromFavoritesMessage by viewModel.showRemovedFromFavoritesMessage.collectAsState()

    // Use this to allow scrolling of the main content
    val scrollState = rememberScrollState()
    
    // Check if current displayed message is a favorite
    LaunchedEffect(displayedMessage) {
        if (displayedMessage.isNotEmpty() && displayedMessage != "Message appears here") {
            viewModel.isMessageFavorite(displayedMessage) { isFav ->
                isFavorite = isFav
            }
        } else {
            isFavorite = false
        }
    }
    
    // Update displayed message when voice recognition completes
    LaunchedEffect(viewModel.message.value) {
        viewModel.message.value?.let { message ->
            if (message.isNotEmpty()) {
                displayedMessage = message
                // Check if the message is a favorite
                viewModel.isMessageFavorite(message) { isFav ->
                    isFavorite = isFav
                }
            }
        }
    }

    // Show delete confirmation dialog if needed
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (displayedMessage.isNotEmpty() && displayedMessage != "Message appears here") {
                            viewModel.removeSavedMessage(displayedMessage)
                            displayedMessage = ""
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Content column that doesn't resize with keyboard
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Saved Messages & Favorites with improved layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Saved Messages button with improved spacing
                IconButton(
                    onClick = { showSavedMessagesScreen = true },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp) // Fixed width to prevent text clipping
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE6F0FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.menu_icon),
                                contentDescription = "Saved Messages",
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Saved",
                            fontSize = 12.sp,
                            color = Color(0xFF6C757D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Favorites button with improved spacing
                IconButton(
                    onClick = {
                        if (displayedMessage.isNotEmpty() && displayedMessage != "Message appears here") {
                            if (isFavorite) {
                                // Immediately update UI
                                isFavorite = false
                                // Then update database
                                viewModel.removeFromFavorites(displayedMessage)
                            } else {
                                // Immediately update UI
                                isFavorite = true
                                // Then update database
                                viewModel.addToFavorites(displayedMessage)
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp) // Fixed width to prevent text clipping
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE6F0FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isFavorite) R.drawable.starred_icon else R.drawable.star_icon
                                ),
                                contentDescription = "Favorites",
                                tint = if (isFavorite) Color(0xFFFF9500) else Color(0xFF007AFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Favs",
                            fontSize = 12.sp,
                            color = Color(0xFF6C757D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Center: Signboard for displaying the message.
            // Now truly centered in the screen with weight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // This makes it take available space and center vertically
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Show partial transcription if listening
                    if (isListening && partialTranscription.isNotEmpty()) {
                        Text(
                            text = partialTranscription,
                            color = Color.Gray,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Main displayed message
                    Text(
                        text = if (displayedMessage.isNotEmpty()) displayedMessage else "Message appears here",
                        color = if (displayedMessage.isNotEmpty()) Color.Black else Color.LightGray,
                        fontSize = if (displayedMessage.isNotEmpty()) 36.sp else 48.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp, // Improved line height for better readability
                        style = TextStyle(
                            lineBreak = LineBreak.Simple, // Better line breaking
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (displayedMessage.isNotEmpty() && displayedMessage != "Message appears here") {
                                            showDeleteDialog = true
                                        }
                                    }
                                )
                            }
                    )
                    
                    // Show recording duration if listening
                    if (isListening) {
                        Card(
                            modifier = Modifier
                                .padding(top = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFF9500)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.microphone_icon),
                                    contentDescription = "Recording",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Recording: $recordingDuration",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Row: Multi-line TextField and Microphone Button.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 16.dp),
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
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Start,
                        lineHeight = 20.sp // Better line spacing for input
                    ),
                    singleLine = false, // Allow multi-line input.
                    maxLines = Int.MAX_VALUE,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (typedMessage.isNotEmpty()) {
                                displayedMessage = typedMessage
                                // Save the message to the database
                                viewModel.saveMessage(typedMessage)
                                // Clear the input field
                                typedMessage = ""
                                // Check if the message is a favorite
                                viewModel.isMessageFavorite(displayedMessage) { isFav ->
                                    isFavorite = isFav
                                }
                                // Hide keyboard
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                IconButton(
                    onClick = { 
                        if (isListening) {
                            viewModel.stopListening()
                            // When stopping, save the transcribed message
                            viewModel.message.value?.let { message ->
                                if (message.isNotEmpty()) {
                                    displayedMessage = message
                                    viewModel.saveMessage(message)
                                }
                            }
                            Toast.makeText(context, "Stopped listening", Toast.LENGTH_SHORT).show()
                        } else {
                            // Check model initialization status
                            when (viewModel.modelInitStatus.value) {
                                ModelInitStatus.NOT_INITIALIZED, ModelInitStatus.INITIALIZING -> {
                                    Toast.makeText(context, "Voice recognition is initializing, please wait...", Toast.LENGTH_LONG).show()
                                }
                                ModelInitStatus.FAILED -> {
                                    Toast.makeText(context, "Voice recognition initialization failed. Retrying...", Toast.LENGTH_LONG).show()
                                }
                                ModelInitStatus.INITIALIZED -> {
                                    Toast.makeText(context, "Starting voice recognition...", Toast.LENGTH_SHORT).show()
                                }
                            }
                            viewModel.startListening()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.microphone_icon),
                        contentDescription = "Microphone",
                        tint = if (isListening) Color(0xFFFF9500) else Color(0xFF007AFF),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Notification popups
        AnimatedVisibility(
            visible = showAddedToFavoritesMessage,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Added to favorites",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        
        AnimatedVisibility(
            visible = showRemovedFromFavoritesMessage,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE57373)
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Removed from favorites",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Overlay for Saved Messages Screen.
        if (showSavedMessagesScreen) {
            SavedMessagesScreen(
                viewModel = viewModel,
                onClose = { showSavedMessagesScreen = false },
                onMessageSelected = { selectedMessage ->
                    // Update displayed message from saved selection.
                    displayedMessage = selectedMessage
                    // Check if the selected message is a favorite
                    viewModel.isMessageFavorite(selectedMessage) { isFav ->
                        isFavorite = isFav
                    }
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
