package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.example.quilacarne.ui.theme.*
import java.util.UUID
import com.example.quilacarne.ui.QuiLaCarneHeader

data class OrderItemWithDish(val name: String, val quantity: Int, val isSpecial: Boolean = false)

@Composable
fun TableDetailScreen(
    navController: NavController,
    tableId: UUID,
    tableName: String
) {
    val grayDark = Color(0xFF3A3A3A)
    val cardBg = Color(0xFFF1F1F1)

    val orderedItems = listOf(
        OrderItemWithDish("Pizza Americano", 1),
        OrderItemWithDish("Sałatka grecka", 2, isSpecial = true),
        OrderItemWithDish("Spaghetti", 1)
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
    ) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 160.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tableName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3E3E3E))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Zajęty", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { /* Status change logic */ },
                colors = ButtonDefaults.buttonColors(containerColor = green),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Zmień status", fontSize = 20.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* Change table logic */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Zmień stolik", fontSize = 20.sp, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Zamówiono:",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .shadow(6.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().background(cardBg).padding(16.dp)) {
                    orderedItems.forEach { item ->
                        Text(
                            text = "${item.name} x${item.quantity}",
                            fontSize = if (item.isSpecial) 22.sp else 18.sp,
                            fontWeight = if (item.isSpecial) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (item.isSpecial) green else Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(grayDark),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Dodatki:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Sos pomidorowy\nOliwa z oliwek", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ActionButton(text = "Zgłoś klienta") { /* Report logic */ }
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(text = "Edytuj zamówienie") { /* Edit logic */ }

            Spacer(modifier = Modifier.height(14.dp))

            StatusPanel()
        }
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp).border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusPanel() {
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black).padding(8.dp)) {
        Text("Jesteś offline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Błąd logowania - spróbuj ponownie", fontSize = 12.sp)
        Text("Zalogowano pomyślnie (Local Cache)", fontSize = 12.sp, color = Color.Gray)
    }
}