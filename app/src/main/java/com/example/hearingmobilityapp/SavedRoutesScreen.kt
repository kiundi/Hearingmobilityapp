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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dummy data model for saved routes.
data class SavedRoute(val id: String, val name: String, val details: String)

@Composable
fun SavedRoutesScreen(
    viewModel: CommunicationViewModel, // Now using CommunicationViewModel instead of SharedViewModel.
    onClose: () -> Unit,
    onRouteSelected: (SavedRoute) -> Unit
) {
    // For now, we define a dummy list. Later, connect to the database or view model.
    val savedRoutes = remember {
        listOf(
            SavedRoute("1", "Home to Office", "Via Main St"),
            SavedRoute("2", "Office to Gym", "Via 2nd Ave")
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Faded background overlay.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        // Sidebar panel sliding from the left.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Saved Routes",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (savedRoutes.isEmpty()) {
                Text("No saved routes yet.", color = Color.Gray, fontSize = 16.sp)
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(savedRoutes) { route ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = Color.LightGray.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onRouteSelected(route)
                                    onClose()
                                }
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(text = route.name, fontSize = 16.sp, color = Color.Black)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = route.details, fontSize = 14.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
