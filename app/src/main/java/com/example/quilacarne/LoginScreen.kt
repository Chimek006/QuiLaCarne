package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController

@Composable
fun LoginScreen(navController: NavController) {

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAEAEA)),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "QuiLaCarne",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(40.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(20.dp)
            ) {

                Column(
                    modifier = Modifier
                        .padding(30.dp)
                        .width(250.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it },
                        label = { Text("Login / Email") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Hasło") }
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Button(
                        onClick = {
                            navController.navigate("tables")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1FA000)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zaloguj się")
                    }

                }
            }
        }
    }
}

