package com.example.quilacarne

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quilacarne.ui.theme.QuiLaCarneTheme

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

                    composable("login") {
                        LoginScreen(navController)
                    }

                    composable("tables") {
                        TablesScreen(navController)
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