package com.example.quilacarne.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.quilacarne.ui.components.QuiLaCarneHeader
import com.example.quilacarne.ui.i18n.AppLanguage
import com.example.quilacarne.ui.i18n.rememberAppLanguage
import com.example.quilacarne.ui.theme.green
import com.example.quilacarne.ui.viewmodels.SyncUiState
import com.example.quilacarne.ui.viewmodels.SyncViewModel

@Composable
fun SyncScreen(
    navController: NavController,
    viewModel: SyncViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val language = rememberAppLanguage()

    LaunchedEffect(Unit) {
        viewModel.startSync()
    }

    if (state is SyncUiState.Success || state is SyncUiState.OfflineAvailable) {
        LaunchedEffect(state) {
            if (state is SyncUiState.Success || state is SyncUiState.OfflineAvailable) {
                navController.navigate("main") {
                    popUpTo("sync") { inclusive = true }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        QuiLaCarneHeader(showBack = false)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                when (val s = state) {
                    is SyncUiState.Loading -> {
                        CircularProgressIndicator(
                            color = green,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = localizeSyncMessage(s.message, language),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = green,
                            trackColor = Color.LightGray
                        )
                    }
                    is SyncUiState.Error -> {
                        Text(
                            text = language.choose("Ups! Cos poszlo nie tak", "Oops! Something went wrong"),
                            color = Color.Red,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = localizeSyncMessage(s.message, language),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.startSync() },
                            colors = ButtonDefaults.buttonColors(containerColor = green),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text(language.choose("Ponow probe", "Try again"), color = Color.White)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun localizeSyncMessage(message: String, language: AppLanguage): String {
    return when {
        message.startsWith("Synchronizacja danych") ->
            language.choose("Synchronizacja danych...", "Syncing data...")
        message.startsWith("Blad synchronizacji") || message.startsWith("Błąd synchronizacji") ->
            language.choose(message, "Sync error: ${message.substringAfter(": ", message)}")
        else -> message
    }
}
