package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController

@Composable
fun SignupScreen(
    navController: NavHostController,
    onGoogleSignIn: () -> Unit,
    onCreateAccount: (String, String, String, String, (String) -> Unit) -> Unit // Updated onCreateAccount callback
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") } // State for error message

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Welcome to Digital Matatu\nCreate Account",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "Sign up to get started",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Name Input
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_user), contentDescription = "User Icon") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_email), contentDescription = "Email Icon") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Phone Input
            /* OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_phone), contentDescription = "Phone Icon") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp)) */

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_lock), contentDescription = "Lock Icon") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Display Error Message (Inserted here)
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Create Account Button
            Button(
                onClick = {
                    onCreateAccount(fullName, email, phone, password) { message ->
                        errorMessage = message
                        Log.e("SignupScreen", "Sign-up failed: $message")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0057FF))
            ) {
                Text("Create Account", color = Color.White)
            }
            Spacer(modifier = Modifier.height(20.dp))

            // OR Separator
            Text(text = "OR WITH", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            // Google Sign-in Button
            OutlinedButton(
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_google), contentDescription = "Google Icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue with Google", color = Color.Black)
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Already have an account?
            Text(
                text = "Already have an account? Login",
                color = Color(0xFF0057FF),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { navController.navigate("login") }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignupScreenPreview() {
    val context: Context = LocalContext.current
    val navController = NavController(context) as NavHostController
    SignupScreen(
        navController = navController,
        onGoogleSignIn = {},
        onCreateAccount = { _, _, _, _, _ -> } // Updated preview
    )
}