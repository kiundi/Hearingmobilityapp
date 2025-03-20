package com.example.hearingmobilityapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

@Composable
fun ChatbotScreen() {
    val firestore = FirebaseFirestore.getInstance()
    var userInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    val openAiApiKey = "YOUR_OPENAI_API_KEY"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chat Messages Display
        Column(modifier = Modifier.weight(1f)) {
            chatMessages.forEach { (message, isUser) ->
                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = if (isUser) Color.Blue else Color.Black,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        // Input Field
        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("Ask about matatu stages...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )

        // Send Button
        Button(
            onClick = {
                if (userInput.isNotEmpty()) {
                    chatMessages = chatMessages + Pair(userInput, true)
                    fetchMatatuStages(userInput, firestore) { response ->
                        chatMessages = chatMessages + Pair(response, false)
                    }
                    userInput = ""
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Send")
        }
    }
}

// Function to fetch matatu stages from Firestore
fun fetchMatatuStages(query: String, firestore: FirebaseFirestore, onResult: (String) -> Unit) {
    firestore.collection("matatu_stages")
        .whereEqualTo("location", query)
        .get()
        .addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val stages = documents.documents[0].get("stages") as List<String>
                onResult("Matatu Stages: ${stages.joinToString(", ")}")
            } else {
                // If no match found in Firestore, ask AI
                fetchAiResponse(query, onResult)
            }
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error fetching stages", e)
            onResult("Error fetching matatu stages.")
        }
}

// Function to fetch response from OpenAI
fun fetchAiResponse(query: String, onResult: (String) -> Unit) {
    val client = OkHttpClient()
    val requestBody = """
        {
            "model": "gpt-3.5-turbo",
            "messages": [{"role": "system", "content": "You are a chatbot that provides matatu stage locations in Kenya."},
                         {"role": "user", "content": "$query"}]
        }
    """.trimIndent()

    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
        .addHeader("Authorization", "Bearer YOUR_OPENAI_API_KEY")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("OpenAI", "API Call Failed", e)
            onResult("I couldn't find matatu stages for that location.")
        }

        override fun onResponse(call: Call, response: Response) {
            response.body?.string()?.let { jsonString ->
                val jsonObject = JSONObject(jsonString)
                val reply = jsonObject.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                onResult(reply)
            }
        }
    })
}

@Preview(showBackground = true)
@Composable
fun ChatbotScreenPreview() {
    ChatbotScreen()
}

