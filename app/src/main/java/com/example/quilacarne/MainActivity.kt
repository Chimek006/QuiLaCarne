package com.example.quilacarne

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.QuiLaCarneHeader
import java.util.UUID
import kotlin.concurrent.thread
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RetrofitClient.init(applicationContext)

        thread {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.openHelper.writableDatabase
                Log.d("QUI_LA_CARNE", "Database connection forced successfully")
            } catch (e: Exception) {
                Log.e("QUI_LA_CARNE", "Failed to force database connection: ${e.message}")
            }
        }

        setContent {
            QuiLaCarneTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") { LoginScreen(navController) }
                    composable("main") { MainScreen(navController) }
                    composable("tables") { TablesScreen(navController) }
                    composable("menu") { MenuScreen(navController) }
                    composable("settings") { PlaceholderScreen(navController, "Ustawienia") }

                    composable("table/{tableId}/{tableName}/{status}") { backStackEntry ->
                        val tableIdString = backStackEntry.arguments?.getString("tableId")
                        val tableName = backStackEntry.arguments?.getString("tableName") ?: "Stolik"
                        val status = backStackEntry.arguments?.getString("status") ?: "Wolny"

                        val tableId = try {
                            UUID.fromString(tableIdString)
                        } catch (e: Exception) {
                            UUID.nameUUIDFromBytes(tableIdString?.toByteArray() ?: ByteArray(0))
                        }

                        TableDetailScreen(
                            navController = navController,
                            tableId = tableId,
                            tableName = tableName,
                            tableStatus = status
                        )
                    }

                    composable("sync") { SyncScreen(navController) }

                    composable(route = "dish_detail/{dishId}") { backStackEntry ->
                        val dishIdString = backStackEntry.arguments?.getString("dishId") ?: ""

                        val dishId = try {
                            UUID.fromString(dishIdString)
                        } catch (e: Exception) {
                            UUID.nameUUIDFromBytes(dishIdString.toByteArray())
                        }

                        DishDetailScreen(
                            dishId = dishId,
                            navController = navController,
                            viewModel = viewModel()
                        )
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
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
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
}