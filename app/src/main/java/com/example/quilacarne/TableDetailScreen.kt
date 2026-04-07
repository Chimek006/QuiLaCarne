package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
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
import androidx.navigation.NavController
import com.example.quilacarne.ui.theme.*

@Composable
fun TopBar(
    navController: NavController,
    showBack: Boolean = false
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zielony pasek na samej górze
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(green)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Kontener na Logo i Strzałkę
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // STRZAŁKA (tylko jeśli showBack == true)
                if (showBack) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Cofnij",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // LOGO QuiLaCarne
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Qui", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = red)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "La", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = green)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Carne", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = green)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Kolorowy pasek separatora
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(horizontal = 18.dp)
            ) {
                Box(Modifier.weight(1f).fillMaxHeight().background(green, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                Box(Modifier.weight(2f).fillMaxHeight().background(Color(0xFFF3F3F3)))
                Box(Modifier.weight(1f).fillMaxHeight().background(red, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
            }
        }
    }
}

@Composable
fun TableDetailScreen(
    navController: NavController,
    tableName: String = "Stolik x"
) {
    val green = Color(0xFF1FA000)
    val grayDark = Color(0xFF3A3A3A)
    val cardBg = Color(0xFFF1F1F1)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
    ) {
        TopBar(navController = navController, showBack = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 140.dp, bottom = 12.dp),
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
                    Text(text = "Status", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { /* TODO: open status dialog */ },
                colors = ButtonDefaults.buttonColors(containerColor = green),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Zmień status", fontSize = 20.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* TODO: change table */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
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
                    .heightIn(min = 180.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg)
                    .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Pizza Americano", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Sałatka grecka", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = green)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Spaghetti", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(grayDark),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Dodatki:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Sos pomidorowy\nOliwa z oliwek", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* zgłoś klienta */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Zgłoś klienta")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { /* TODO edit order */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Edytuj zamówienie")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .padding(8.dp)
            ) {
                Text("Jesteś offline", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Błąd logowania - spróbuj ponownie", fontSize = 14.sp)
                Text("Nieprawidłowe hasło", fontSize = 14.sp)
                Text("Nie znaleziono konta", fontSize = 14.sp)
                Text("Zalogowano pomyślnie", fontSize = 14.sp)
            }
        }
    }
}