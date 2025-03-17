package com.example.hearingmobilityapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun EmergencyContactsScreen() {
    val context = LocalContext.current

    var name by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }

    // Stores emergency contacts
    var contacts by remember { mutableStateOf(mutableListOf<Pair<String, String>>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Add Emergency Contact", style = MaterialTheme.typography.h6)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Contact Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (name.text.isNotEmpty() && phone.text.isNotEmpty()) {
                    contacts = (contacts + (name.text to phone.text)).toMutableList()
                    name = TextFieldValue("") // Clear input fields
                    phone = TextFieldValue("")
                } else {
                    Toast.makeText(context, "Please enter name and phone", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Contact")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Emergency Contacts", style = MaterialTheme.typography.h6)

        LazyColumn {
            items(contacts) { contact ->
                ContactItem(contact.first, contact.second, onCall = { makeEmergencyCall(context, contact.second) })
            }
        }
    }
}

@Composable
fun ContactItem(name: String, phone: String, onCall: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCall() },
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = name, style = MaterialTheme.typography.body1)
                Text(text = phone, style = MaterialTheme.typography.body2)
            }
            Button(onClick = { onCall() }) {
                Text("Call")
            }
        }
    }
}

fun makeEmergencyCall(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
    }
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
fun EmergencyContactsScreenPreview() {
    EmergencyContactsScreen()
}
