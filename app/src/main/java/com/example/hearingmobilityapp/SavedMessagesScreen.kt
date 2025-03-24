package com.example.hearingmobilityapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SavedMessages(val id: String, val text: String)

@Composable
fun SavedMessagesScreen(
    viewModel: CommunicationViewModel,
    onClose: () -> Unit,
    onMessageSelected: (String) -> Unit
) {
    val savedMessages by viewModel.savedMessages.collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        // Faded background of the communication page
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.3f)) // Adjust alpha for fade intensity
        )

        // Saved messages screen on the left
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp) // Adjust width as needed
                .background(Color.White)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Saved Messages",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(48.dp)) // To align with back button
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (savedMessages.isEmpty()) {
                Text("No saved messages yet.", color = Color.Gray)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(savedMessages) { message ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = Color.LightGray.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onMessageSelected(message.text)
                                    onClose()
                                }
                                .padding(16.dp)
                        ) {
                            Text(text = message.text, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}