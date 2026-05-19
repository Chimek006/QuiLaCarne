package com.example.quilacarne.ui.screens

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
import com.example.quilacarne.ui.components.QuiLaCarneHeader
import com.example.quilacarne.ui.i18n.AppLanguage
import com.example.quilacarne.ui.i18n.rememberAppLanguage
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.viewmodels.TableUiState
import com.example.quilacarne.ui.viewmodels.TablesViewModel

@Composable
fun TablesScreen(navController: NavController, viewModel: TablesViewModel) {
    val tables by viewModel.tableUiStates.collectAsState()
    val isSyncComplete by viewModel.isSyncComplete.collectAsState()
    val language = rememberAppLanguage()

    val isLoading = tables.isEmpty() && !isSyncComplete

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFDFDFD))) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 160.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = language.choose("Lista stolikow", "Tables"),
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
                    Text(language.choose("Brak stolikow do wyswietlenia", "No tables to display"), color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tables, key = { it.id }) { table ->
                        val statusText = table.localizedStatusName(language)
                        val (pillColor, textColor) = getStatusColors(table.statusToken)

                        TableCard(
                            name = language.choose("Stolik ${table.tableNumber}", "Table ${table.tableNumber}"),
                            status = statusText,
                            waiterName = table.waiterName,
                            reservationTime = table.reservationTime,
                            language = language,
                            pillColor = pillColor,
                            pillTextColor = textColor,
                            topColor = darkGreen,
                            bottomColor = green,
                            onClick = { name ->
                                val encodedName = Uri.encode(name)
                                val encodedStatus = Uri.encode(table.statusToken)
                                navController.navigate("table/${table.id}/$encodedName/$encodedStatus")
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun TableUiState.localizedStatusName(language: AppLanguage): String {
    return language.choose(statusNamePl, statusNameEn)
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
    reservationTime: String?,
    language: AppLanguage,
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

                    if (reservationTime != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "${language.choose("Godziny", "Time")}: $reservationTime",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    } else if (waiterName != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "${language.choose("Kelner", "Waiter")}: $waiterName",
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
