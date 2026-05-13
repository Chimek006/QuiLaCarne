package com.example.quilacarne.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.quilacarne.ui.components.QuiLaCarneHeader
import com.example.quilacarne.ui.i18n.AppLanguage
import com.example.quilacarne.ui.i18n.AppLanguageStore
import com.example.quilacarne.ui.i18n.rememberAppLanguage
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.viewmodels.MainViewModel

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val language = rememberAppLanguage()

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(localizeMainMessage(message, language))
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) {
            navController.navigate("login") {
                popUpTo("main") {
                    inclusive = true
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFFDFDFD)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFDFDFD))
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QuiLaCarneHeader(navController = navController, showBack = false)

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = language.choose("Witaj w panelu!", "Welcome to the panel!"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkGray
                )

                Spacer(modifier = Modifier.height(30.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        title = language.choose("Lista stolikow", "Tables"),
                        icon = Icons.Default.List,
                        color = green,
                        onClick = { navController.navigate("tables") }
                    )

                    DashboardCard(
                        title = language.choose("Menu restauracji", "Restaurant menu"),
                        icon = Icons.Default.Menu,
                        color = orange,
                        onClick = { navController.navigate("menu") }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                MainBottomActions(
                    language = language,
                    isLoggingOut = state.isLoggingOut,
                    onLanguageChange = { AppLanguageStore.setLanguage(context.applicationContext, it) },
                    onSync = { navController.navigate("sync") },
                    onLogout = viewModel::logout
                )

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

private fun localizeMainMessage(message: String, language: AppLanguage): String {
    return when (message) {
        "Wylogowano" -> language.choose("Wylogowano", "Logged out")
        "Wylogowano lokalnie" -> language.choose("Wylogowano lokalnie", "Logged out locally")
        else -> message
    }
}

@Composable
private fun MainBottomActions(
    language: AppLanguage,
    isLoggingOut: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LanguageSelector(
                language = language,
                enabled = !isLoggingOut,
                onLanguageChange = onLanguageChange,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            )

            Button(
                onClick = onSync,
                enabled = !isLoggingOut,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text(language.choose("Synchronizuj", "Sync"), color = Color.White, fontSize = 15.sp)
            }
        }

        Button(
            onClick = onLogout,
            enabled = !isLoggingOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(language.choose("Wyloguj", "Log out"), color = Color.White, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    language: AppLanguage,
    enabled: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = language.choose("Jezyk: polski", "Language: English"),
                color = Color.Black,
                fontSize = 14.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Polski") },
                onClick = {
                    expanded = false
                    onLanguageChange(AppLanguage.Polish)
                }
            )
            DropdownMenuItem(
                text = { Text("English") },
                onClick = {
                    expanded = false
                    onLanguageChange(AppLanguage.English)
                }
            )
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = white),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = color
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }
    }
}
