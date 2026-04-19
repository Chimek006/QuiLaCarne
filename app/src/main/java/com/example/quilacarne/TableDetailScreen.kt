package com.example.quilacarne

import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.viewmodels.TableDetailViewModel
import java.util.UUID
import java.util.Locale
import com.example.quilacarne.ui.QuiLaCarneHeader

@Composable
fun TableDetailScreen(
    navController: NavController,
    tableId: UUID,
    tableName: String,
    tableStatus: String = "AVAILABLE",
    viewModel: TableDetailViewModel = viewModel()
) {
    val orderItems by viewModel.orderItems.collectAsState()
    val statusDict by viewModel.statusDictionary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(tableId) {
        viewModel.loadTableData(tableId)
    }

    val effectiveToken = remember(orderItems, tableStatus) {
        val remoteStatus = tableStatus.uppercase()
        if (remoteStatus == "AVAILABLE" && orderItems.isNotEmpty()) {
            "OCCUPIED"
        } else {
            remoteStatus
        }
    }

    val statusInfo = statusDict.find { it.token.uppercase() == effectiveToken }

    val displayStatusName = statusInfo?.namePl ?: when (effectiveToken) {
        "AVAILABLE" -> "Wolny"
        "OCCUPIED" -> "Zajęty"
        "RESERVED" -> "Zarezerwowany"
        "CLEANING" -> "Do sprzątnięcia"
        "OUT_OF_SERVICE" -> "Wyłączony"
        else -> effectiveToken.lowercase().replaceFirstChar { it.uppercase() }
    }

    val (statusPillColor, statusTextColor) = getStatusColorsByToken(effectiveToken)

    val totalPrice = remember(orderItems) {
        orderItems.fold(0.0) { acc, wrapper ->
            acc + (wrapper.item.priceAtTimeOfOrder.toDouble() * wrapper.item.quantity)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 140.dp, bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Nazwa i Status Pill
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tableName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusPillColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = displayStatusName,
                        color = statusTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ActionButtons()

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Zamówiono:",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isLoading && orderItems.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = green)
                        }
                    } else if (orderItems.isEmpty()) {
                        Text(
                            text = if (effectiveToken == "AVAILABLE") "Brak pozycji - stolik wolny" else "Brak aktywnych zamówień",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    } else {
                        orderItems.forEachIndexed { index, wrapper ->
                            OrderItemRow(
                                name = wrapper.dish?.name ?: "Danie nieznane",
                                quantity = wrapper.item.quantity,
                                status = "W kuchni",
                                price = wrapper.item.priceAtTimeOfOrder.toDouble(),
                                accentColor = Color.Gray
                            )
                            if (index < orderItems.size - 1) {
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            PriceSummary(totalPrice)

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { /* TODO: Implementacja zgłaszania klienta */ },
                colors = ButtonDefaults.buttonColors(containerColor = orange),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Zgłoś klienta", fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun getStatusColorsByToken(token: String): Pair<Color, Color> {
    return when (token) {
        "AVAILABLE" -> green to Color.Black
        "OCCUPIED", "CLEANING" -> Color(0xFF3A3A3A) to Color.White
        "RESERVED" -> orange to Color.Black
        "OUT_OF_SERVICE" -> Color.Red to Color.White
        else -> Color.LightGray to Color.Black
    }
}

@Composable
fun OrderItemRow(name: String, quantity: Int, status: String, price: Double, accentColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = status.uppercase(),
            fontSize = 9.sp,
            color = accentColor,
            modifier = Modifier
                .width(85.dp)
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
        Text(text = "$name x$quantity", fontSize = 15.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)

        val formattedPrice = String.format(Locale.US, "%.2f", (price * quantity) / 100.0)
        Text(
            text = "$formattedPrice zł",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun PriceSummary(totalPrice: Double) {
    Spacer(modifier = Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Cena łączna:", fontSize = 20.sp, fontWeight = FontWeight.Light)

        val formattedTotal = String.format(Locale.US, "%.2f", totalPrice / 100.0)
        Text(text = "$formattedTotal zł", fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ActionButtons() {
    Column {
        Button(
            onClick = { /* TODO */ },
            colors = ButtonDefaults.buttonColors(containerColor = green),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Zmień status", color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { /* TODO */ },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Edytuj zamówienie", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { /* TODO */ },
            colors = ButtonDefaults.buttonColors(containerColor = green),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Zmień stolik", color = Color.White)
        }
    }
}