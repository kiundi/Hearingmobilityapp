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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
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

@Composable
fun SavedMessagesScreen(
    viewModel: CommunicationViewModel,
    onClose: () -> Unit,
    onMessageSelected: (String) -> Unit
) {
    val savedMessages by viewModel.savedMessages.collectAsState(initial = emptyList())
    val favoriteMessages by viewModel.favoriteMessages.collectAsState(initial = emptyList())
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Faded background overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { onClose() }
        )
        // Slide-in panel from the left for saved messages
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(Color.White, shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Saved Messages",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                // Spacer to balance layout (same width as the IconButton)
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Only show favorite messages
            if (favoriteMessages.isEmpty()) {
                Text("No favorite messages yet.", color = Color.Gray, fontSize = 16.sp)
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(favoriteMessages) { message ->
                        MessageItem(
                            message = message,
                            onMessageClick = { 
                                onMessageSelected(message.text)
                                onClose() 
                            },
                            onFavoriteToggle = { 
                                viewModel.removeFromFavorites(message.text)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: SavedMessage,
    onMessageClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = Color.LightGray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onMessageClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = message.text,
            fontSize = 16.sp,
            color = Color.Black,
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        )
        
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier.size(32.dp)
        ) {
            if (message.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Remove from favorites",
                    tint = Color(0xFFFF9500)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = "Add to favorites",
                    tint = Color(0xFF007AFF)
                )
            }
        }
    }
}
