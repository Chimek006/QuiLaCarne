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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.quilacarne.ui.theme.*

@Composable
fun TablesScreen(navController: NavController) {

    // Dane stolików
    val tables = listOf(
        TableData("Stolik I", "Wolny", Color(0xFF00D34A), Color.Black),
        TableData("Stolik II", "#1010", white, Color.Black),
        TableData("Stolik III", "Rezerwacja", orange, Color.Black),
        TableData("Stolik IV", "Do sprzątania", darkGray, Color.White)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFDFDFD))) {

        // 1. Nagłówek ze strzałką i logo (showBack = true)
        TopBar(navController = navController, showBack = true)

        // 2. Główna treść - zaczyna się pod nagłówkiem (140.dp to bezpieczny margines)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 140.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = "Lista stolików",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Siatka stolików
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tables) { table ->
                    TableCard(
                        name = table.name,
                        status = table.status,
                        pillColor = table.pillColor,
                        pillTextColor = table.pillTextColor,
                        topColor = topColor,     // Kolory z Twojego theme
                        bottomColor = bottomColor, // Kolory z Twojego theme
                        onClick = { name ->
                            val encoded = java.net.URLEncoder.encode(name, "utf-8")
                            navController.navigate("table/$encoded")
                        }
                    )
                }
            }
        }
    }
}

// Model danych stolika
data class TableData(
    val name: String,
    val status: String,
    val pillColor: Color,
    val pillTextColor: Color
)

// Komponent pojedynczej karty stolika
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
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Góra karty (Nazwa)
            Box(
                modifier = Modifier
                    .weight(1f)
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
                        .shadow(6.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = status,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pillTextColor
                    )
                }
            }
        }
    }
}