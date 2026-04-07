package com.example.quilacarne

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quilacarne.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuiLaCarneTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "login"
                ) {
                    composable("login") { LoginScreen(navController) }
                    composable("main") { MainScreen(navController) }
                    composable("tables") { TablesScreen(navController) }

                    composable("menu") {
                        PlaceholderScreen(navController, "Menu")
                    }

                    composable("settings") {
                        PlaceholderScreen(navController, "Ustawienia")
                    }

                    composable("table/{tableName}") { backStackEntry ->
                        val tableName = backStackEntry.arguments?.getString("tableName") ?: "Stolik"
                        TableDetailScreen(navController, tableName)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(navController: NavController, title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopBar(navController = navController)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = darkGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ten ekran jest w budowie",
                fontSize = 18.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = green),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Wróć do Dashboardu", color = Color.White)
            }
        }
    }
}