package com.example.hearingmobilityapp

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CommunicationPageWithPermission() {
    val recordAudioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    when (recordAudioPermissionState.status) {
        PermissionStatus.Granted -> {
            // Permission is granted, show your communication UI
            CommunicationPage()
        }

        is PermissionStatus.Denied -> {
            // If permission was denied but we can ask again, or it's the first time
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("This feature requires microphone access.")
                Button(onClick = { recordAudioPermissionState.launchPermissionRequest() }) {
                    Text("Request Permission")
                }
            }
            // Optionally, you can automatically launch the permission request:
            if (!(recordAudioPermissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                SideEffect {
                    recordAudioPermissionState.launchPermissionRequest()
                }
            }
        }
    }
}
@Composable
fun CommunicationPage() {
    // State for the typed or transcribed message
    var message by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Row: Saved Messages & Add to Favourites
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left icon + label (Menu / Saved Messages)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = R.drawable.menu_icon),
                    contentDescription = "Saved Messages Icon",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "saved messages",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            // Right icon + label (Star / Favourites)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = R.drawable.star_icon),
                    contentDescription = "Add to Favourites Icon",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "add to favourites",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        // Center: Large text area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 50.dp, bottom = 100.dp), // Keeps offset from top & bottom
            contentAlignment = Alignment.Center // Centers everything inside the Box
        ) {
            Text(
                text = message.ifEmpty { "Message appears here" },
                color = if (message.isEmpty()) Color.LightGray else Color.Black,
                fontSize = 50.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(), // Ensures text doesn't shrink weirdly
                maxLines = 3,
            )
        }


        // Bottom Row: TextField + Microphone
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TextField
            TextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Type message here...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // The message is updated automatically via onValueChange
                        // You can handle 'Done' action here if needed
                    }
                )
            )

            // Microphone Icon
            IconButton(onClick = {
                // TODO: Trigger audio recording -> transcribe -> update 'message'
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.microphone_icon),
                    contentDescription = "Microphone Icon",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
