package com.example.quilacarne

import android.net.Uri
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
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.viewmodels.TablesViewModel
import java.util.UUID

@Composable
fun TablesScreen(navController: NavController, viewModel: TablesViewModel) {
    val tables by viewModel.tables.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val waiterNamesByTable by viewModel.waiterNamesByTable.collectAsState()
    val isSyncComplete by viewModel.isSyncComplete.collectAsState()
    val sortedTables = remember(tables) {
        tables.sortedBy { it.tableNumber }
    }

    val isLoading = sortedTables.isEmpty() && !isSyncComplete

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
            } else if (sortedTables.isEmpty()) {
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
                    items(sortedTables, key = { it.id }) { table ->
                        val statusInfo = getStatusInfo(table.statusId, statuses)
                        val statusToken = statusInfo?.token ?: "AVAILABLE"
                        val statusText = statusInfo?.namePl ?: getStatusText(statusToken)
                        val (pillColor, textColor) = getStatusColors(statusToken)

                        TableCard(
                            name = "Stolik ${table.tableNumber}",
                            status = statusText,
                            waiterName = waiterNamesByTable[table.id],
                            pillColor = pillColor,
                            pillTextColor = textColor,
                            topColor = darkGreen,
                            bottomColor = green,
                            onClick = { name ->
                                val encodedName = Uri.encode(name)
                                val encodedStatus = Uri.encode(statusToken)
                                navController.navigate("table/${table.id}/$encodedName/$encodedStatus")
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun getStatusInfo(statusId: UUID?, statuses: List<TableStatusEntity>): TableStatusEntity? {
    return statuses.find { it.id == statusId }
}

private fun getStatusText(token: String): String {
    return when (token.uppercase()) {
        "AVAILABLE" -> "Wolny"
        "OCCUPIED" -> "Zajęty"
        "RESERVED" -> "Zarezerwowany"
        "CLEANING" -> "Do sprzątnięcia"
        "OUT_OF_SERVICE" -> "Wyłączony"
        else -> "Wolny"
    }
}

private fun getStatusColors(status: String): Pair<Color, Color> {
    return when (status.uppercase()) {
        "AVAILABLE" -> Color(0xFF00D34A) to Color.Black
        "OCCUPIED" -> Color.White to Color.Black
        "RESERVED" -> Color(0xFFFF9800) to Color.Black
        "CLEANING" -> Color(0xFF3A3A3A) to Color.White
        "OUT_OF_SERVICE" -> Color.Red to Color.White
        else -> Color.LightGray to Color.Black
    }
}

@Composable
private fun TableCard(
    name: String,
    status: String,
    waiterName: String?,
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
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

                    waiterName?.let {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "Kelner: $it",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
