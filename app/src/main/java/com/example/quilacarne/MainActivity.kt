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
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.QuiLaCarneHeader
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RetrofitClient.init(applicationContext)

        setContent {
            QuiLaCarneTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") { LoginScreen(navController) }
                    composable("main") { MainScreen(navController) }
                    composable("tables") { TablesScreen(navController) }
                    composable("menu") { PlaceholderScreen(navController, "Menu") }
                    composable("settings") { PlaceholderScreen(navController, "Ustawienia") }

                    composable("table/{tableId}/{tableName}/{status}") { backStackEntry ->
                        val tableIdString = backStackEntry.arguments?.getString("tableId")
                        val tableName = backStackEntry.arguments?.getString("tableName") ?: "Stolik"
                        val status = backStackEntry.arguments?.getString("status") ?: "Wolny"

                        val tableId = try {
                            UUID.fromString(tableIdString)
                        } catch (e: Exception) {
                            UUID.randomUUID()
                        }

                        TableDetailScreen(
                            navController = navController,
                            tableId = tableId,
                            tableName = tableName,
                            tableStatus = status
                        )
                    }
                    composable("sync") { SyncScreen(navController) }
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(navController: NavController, title: String) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        QuiLaCarneHeader(navController = navController, showBack = true)
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(30.dp))
            Button(
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D34A))
            ) {
                Text("Wróć")
            }
        }
    }
}