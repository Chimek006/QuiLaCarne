package com.example.quilacarne

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.models.TableDto
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.QuiLaCarneHeader
import java.net.URLEncoder

@Composable
fun TablesScreen(navController: NavController) {
    var tables by remember { mutableStateOf<List<TableDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val startTime = "2026-04-15T12:00:00.000Z"
            val endTime = "2026-04-15T13:00:00.000Z"

            val response = RetrofitClient.tableService.getTables(
                startTime = startTime,
                endTime = endTime
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.isSuccess == true) {
                    tables = body.data?.tables ?: emptyList()
                    errorMessage = null
                } else {
                    errorMessage = body?.message ?: "Błąd walidacji danych"
                }
            } else {
                val errorDetail = response.errorBody()?.string()
                Log.e("API_ERROR", "Kod: ${response.code()}, Body: $errorDetail")
                errorMessage = "Błąd ${response.code()}: Sprawdź Logcat"
            }
        } catch (e: Exception) {
            Log.e("API_ERROR", "Wyjątek: ${e.message}")
            errorMessage = "Błąd sieci: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFDFDFD))) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 160.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = "Lista stolików",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
                color = Color.Black
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = green)
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = errorMessage!!, color = Color.Red, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tables) { table ->
                        val (pillColor, textColor) = getStatusColors(table.status)

                        TableCard(
                            name = "Stolik ${table.tableNumber}",
                            status = table.status,
                            pillColor = pillColor,
                            pillTextColor = textColor,
                            topColor = Color(0xFFE0E0E0),
                            bottomColor = Color(0xFFF5F5F5),
                            onClick = { name ->
                                val encoded = URLEncoder.encode(name, "utf-8")
                                navController.navigate("table/$encoded")
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun getStatusColors(status: String): Pair<Color, Color> {
    return when (status.lowercase()) {
        "free", "wolny" -> Color(0xFF00D34A) to Color.Black
        "occupied", "zajęty", "1010" -> Color.White to Color.Black
        "reserved", "rezerwacja" -> Color(0xFFFF9800) to Color.Black
        "cleaning", "do sprzątania" -> Color(0xFF3A3A3A) to Color.White
        else -> Color.LightGray to Color.Black
    }
}

@Composable
private fun TableCard(
    name: String,
    status: String,
    pillColor: Color,
    pillTextColor: Color,
    topColor: Color,
    bottomColor: Color,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable { onClick(name) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth()
                    .background(topColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(bottomColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pillTextColor
                    )
                }
            }
        }
    }
}