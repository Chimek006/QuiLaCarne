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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.navigation.NavController

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TablesScreen(navController: NavController) {

    val green = Color(0xFF1FA000)
    val topColor = Color(0xFF149600)
    val bottomColor = Color(0xFF00C800)
    val orange = Color(0xFFFF8A00)
    val white = Color.White
    val darkGray = Color(0xFF3A3A3A)
    val red = Color(0xFFFF2D2D)

    val tables = listOf(
        TableData("Stolik I", "Wolny", Color(0xFF00D34A), Color.Black),
        TableData("Stolik II", "#1010", white, Color.Black),
        TableData("Stolik III", "Rezerwacja", orange, Color.Black),
        TableData("Stolik IV", "Do sprzątania", darkGray, Color.White)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDFDFD))
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(green)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Qui",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = red
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "La",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = green
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Carne",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = green
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(horizontal = 18.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(green, RoundedCornerShape(4.dp))
                )
                Box(
                    Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .background(Color(0xFFF3F3F3))
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(red, RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

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

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tables) { table ->
                    TableCard(
                        name = table.name,
                        status = table.status,
                        pillColor = table.pillColor,
                        pillTextColor = table.pillTextColor,
                        topColor = topColor,
                        bottomColor = bottomColor,
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
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

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