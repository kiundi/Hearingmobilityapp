package com.example.hearingmobilityapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * A composable that provides an input field with location auto-suggestions
 * 
 * @param value The current input value
 * @param onValueChange Callback when the input value changes
 * @param label The label text for the input field
 * @param locationUtils The LocationUtils instance for getting location suggestions
 * @param onLocationSelected Callback when a location is selected, providing the GeoPoint
 */
@Composable
fun LocationInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    locationUtils: LocationUtils,
    onLocationSelected: (GeoPoint) -> Unit
) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Debounce the search
    LaunchedEffect(value) {
        if (value != searchQuery) {
            searchQuery = value
            delay(300) // Debounce delay
            if (value.length >= 3) {
                try {
                    suggestions = locationUtils.getSuggestions(value)
                    showSuggestions = suggestions.isNotEmpty()
                } catch (e: Exception) {
                    suggestions = emptyList()
                    showSuggestions = false
                }
            } else {
                suggestions = emptyList()
                showSuggestions = false
            }
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color(0xFF6C757D)
            )
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                shadowElevation = 4.dp
            ) {
                LazyColumn {
                    items(suggestions) { suggestion ->
                        Text(
                            text = suggestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(suggestion)
                                    showSuggestions = false
                                    coroutineScope.launch {
                                        try {
                                            locationUtils.getCoordinates(suggestion)?.let {
                                                onLocationSelected(it)
                                            }
                                        } catch (e: Exception) {
                                            // Handle error getting coordinates
                                        }
                                    }
                                }
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
} 