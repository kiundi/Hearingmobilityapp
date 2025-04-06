package com.example.hearingmobilityapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog to display and select saved routes
 */
@Composable
fun SavedRoutesDialog(
    viewModel: CommunicationViewModel,
    onDismiss: () -> Unit,
    onRouteSelected: (String) -> Unit
) {
    val savedRoutes = remember { viewModel.getSavedRoutes() }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Saved Routes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (savedRoutes.isEmpty()) {
                    Text(
                        text = "No saved routes found",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(savedRoutes) { route ->
                            val parts = route.split("|")
                            val source = if (parts.isNotEmpty()) parts[0] else ""
                            val destination = if (parts.size > 1) parts[1] else ""
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF5F5F5))
                                    .clickable { 
                                        // Pass the route to the parent component
                                        onRouteSelected(route)
                                    }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "From: $source",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "To: $destination",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
