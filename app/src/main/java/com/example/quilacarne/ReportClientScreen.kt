package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.theme.green
import com.example.quilacarne.ui.theme.lightGray
import com.example.quilacarne.ui.viewmodels.ReportClientUiState
import com.example.quilacarne.ui.viewmodels.ReportClientViewModel
import java.util.UUID

@Composable
fun ReportClientScreen(
    navController: NavController,
    tableId: UUID,
    viewModel: ReportClientViewModel = viewModel()
) {
    var reason by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val canSend = reason.isNotBlank() && description.isNotBlank()
    val isSending = uiState is ReportClientUiState.Sending

    LaunchedEffect(uiState) {
        if (uiState is ReportClientUiState.Sent) {
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Zglos klienta",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Powod zgloszenia") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = reportTextFieldColors()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text("Dokladny opis") },
                shape = RoundedCornerShape(12.dp),
                minLines = 6,
                colors = reportTextFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState is ReportClientUiState.Error) {
                Text(
                    text = (uiState as ReportClientUiState.Error).message,
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Anuluj", color = Color.Black)
                }

                Button(
                    onClick = {
                        viewModel.submitReport(tableId, reason, description)
                    },
                    enabled = canSend && !isSending,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = green,
                        disabledContainerColor = lightGray
                    )
                ) {
                    Text(
                        text = if (isSending) "Wysylanie..." else "Wyslij",
                        color = if (canSend && !isSending) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun reportTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedBorderColor = green,
        unfocusedBorderColor = Color.Gray,
        focusedLabelColor = green,
        unfocusedLabelColor = Color.Gray,
        cursorColor = green,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )
}
