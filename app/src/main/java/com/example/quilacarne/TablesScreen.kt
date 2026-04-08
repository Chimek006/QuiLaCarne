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
import com.example.quilacarne.ui.QuiLaCarneHeader
import java.net.URLEncoder

@Composable
fun TablesScreen(navController: NavController) {

    val tables = listOf(
        TableData("Stolik I", "Wolny", Color(0xFF00D34A), Color.Black),
        TableData("Stolik II", "#1010", Color.White, Color.Black),
        TableData("Stolik III", "Rezerwacja", Color(0xFFFF9800), Color.Black),
        TableData("Stolik IV", "Do sprzątania", Color(0xFF3A3A3A), Color.White)
    )

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

data class TableData(
    val name: String,
    val status: String,
    val pillColor: Color,
    val pillTextColor: Color
)

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