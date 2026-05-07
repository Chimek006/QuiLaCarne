package com.example.quilacarne

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
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.viewmodels.TablesViewModel
import java.net.URLEncoder

@Composable
fun TablesScreen(navController: NavController, viewModel: TablesViewModel) {
    val tables by viewModel.tables.collectAsState()
    val isSyncComplete by viewModel.isSyncComplete.collectAsState()

    val isLoading = tables.isEmpty() && !isSyncComplete

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
            } else if (tables.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Brak stolików do wyświetlenia", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tables) { table ->
                        val statusText = getStatusText(table.statusId.toString())
                        val (pillColor, textColor) = getStatusColors(statusText)

                        TableCard(
                            name = "Stolik ${table.tableNumber}",
                            status = statusText,
                            pillColor = pillColor,
                            pillTextColor = textColor,
                            topColor = darkGreen,
                            bottomColor = green,
                            onClick = { name ->
                                val encodedName = URLEncoder.encode(name, "utf-8")
                                val encodedStatus = URLEncoder.encode(statusText, "utf-8")
                                navController.navigate("table/${table.id}/$encodedName/$encodedStatus")
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun getStatusText(statusId: String): String {
    return when (statusId) {
        "1" -> "Wolny"
        "2" -> "Zajęty"
        else -> "Wolny"
    }
}

private fun getStatusColors(status: String): Pair<Color, Color> {
    return when (status.lowercase()) {
        "available", "wolny" -> Color(0xFF00D34A) to Color.Black
        "occupied", "zajęty" -> Color.White to Color.Black
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
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
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
                    color = Color.White
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