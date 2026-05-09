package com.example.quilacarne

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
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
    val tableStatusId by viewModel.tableStatusId.collectAsState()
    val waiters by viewModel.waiters.collectAsState()
    val activeOrderId by viewModel.activeOrderId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showWaiterDialog by remember { mutableStateOf(false) }
    var pendingOccupiedStatus by remember { mutableStateOf<TableStatusOption?>(null) }
    var stagedStatus by remember(tableId) { mutableStateOf<TableStatusOption?>(null) }
    var stagedWaiter by remember(tableId) { mutableStateOf<UsersEntity?>(null) }
    var isSavingChanges by remember { mutableStateOf(false) }

    LaunchedEffect(tableId) {
        viewModel.loadTableData(tableId)
    }

    val statusOptions = remember(statusDict) {
        buildTableStatusOptions(statusDict)
    }

    val currentStatusInfo = statusDict.find { it.id == tableStatusId }
    val currentToken = currentStatusInfo?.token?.uppercase() ?: normalizeStatusToken(tableStatus)

    val effectiveToken = remember(orderItems, currentToken) {
        if (currentToken == "AVAILABLE" && orderItems.isNotEmpty()) {
            "OCCUPIED"
        } else {
            currentToken
        }
    }

    val displayedToken = stagedStatus?.token ?: effectiveToken
    val statusInfo = statusOptions.find { it.token == displayedToken }

    val displayStatusName = statusInfo?.name ?: when (displayedToken) {
        "AVAILABLE" -> "Wolny"
        "OCCUPIED" -> "Zajęty"
        "RESERVED" -> "Zarezerwowany"
        "CLEANING" -> "Do sprzątnięcia"
        "OUT_OF_SERVICE" -> "Wyłączony"
        else -> displayedToken.lowercase().replaceFirstChar { it.uppercase() }
    }

    val (statusPillColor, statusTextColor) = getStatusColorsByToken(displayedToken)
    val hasPendingChanges = stagedStatus != null || stagedWaiter != null

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

            ActionButtons(
                statusOptions = statusOptions,
                currentStatusToken = displayedToken,
                canEditOrder = activeOrderId != null,
                onStatusSelected = { status ->
                    if (status.token == "OCCUPIED") {
                        pendingOccupiedStatus = status
                        showWaiterDialog = true
                    } else {
                        stagedStatus = status
                        stagedWaiter = null
                    }
                },
                onEditOrder = {
                    activeOrderId?.let { orderId ->
                        navController.navigate("order_add/$tableId/$orderId")
                    }
                }
            )

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
                            text = if (displayedToken == "AVAILABLE") "Brak pozycji - stolik wolny" else "Brak aktywnych zamówień",
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
                onClick = { navController.navigate("report_client/$tableId") },
                colors = ButtonDefaults.buttonColors(containerColor = orange),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Zgłoś klienta", fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SaveChangesButton(
                enabled = hasPendingChanges && !isSavingChanges,
                isSaving = isSavingChanges,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = saveClick@{
                    val status = stagedStatus ?: return@saveClick
                    isSavingChanges = true

                    if (status.token == "OCCUPIED") {
                        val waiter = stagedWaiter
                        if (waiter != null) {
                            viewModel.assignWaiterAndOccupyTable(
                                tableId = tableId,
                                statusToken = status.token,
                                statusName = status.name,
                                waiterId = waiter.id
                            ) { orderId ->
                                stagedStatus = null
                                stagedWaiter = null
                                isSavingChanges = false
                                navController.navigate("order_add/$tableId/$orderId")
                            }
                        } else {
                            isSavingChanges = false
                        }
                    } else {
                        viewModel.changeTableStatus(
                            tableId = tableId,
                            token = status.token,
                            name = status.name
                        ) {
                            stagedStatus = null
                            stagedWaiter = null
                            isSavingChanges = false
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showWaiterDialog) {
            WaiterAssignmentDialog(
                waiters = waiters,
                onDismiss = {
                    showWaiterDialog = false
                    pendingOccupiedStatus = null
                },
                onWaiterSelected = { waiter ->
                    val status = pendingOccupiedStatus
                    if (status != null) {
                        stagedStatus = status
                        stagedWaiter = waiter
                        showWaiterDialog = false
                        pendingOccupiedStatus = null
                    }
                }
            )
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
fun SaveChangesButton(
    enabled: Boolean,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeColor = green
    val inactiveColor = Color(0xFFBDBDBD)
    val borderColor = if (enabled) activeColor else inactiveColor
    val textColor = if (enabled) activeColor else Color(0xFF8E8E8E)

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFF6F6F6),
            disabledContainerColor = Color(0xFFF6F6F6),
            contentColor = textColor,
            disabledContentColor = textColor
        )
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = activeColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text("Zapisz", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionButtons(
    statusOptions: List<TableStatusOption>,
    currentStatusToken: String,
    canEditOrder: Boolean,
    onStatusSelected: (TableStatusOption) -> Unit,
    onEditOrder: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = green),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Zmień status stolika", color = Color.White)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                statusOptions.forEach { status ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (status.token == currentStatusToken) "${status.name} ✓" else status.name,
                                color = Color.Black
                            )
                        },
                        onClick = {
                            expanded = false
                            onStatusSelected(status)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onEditOrder,
            enabled = canEditOrder,
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

@Composable
private fun WaiterAssignmentDialog(
    waiters: List<UsersEntity>,
    onDismiss: () -> Unit,
    onWaiterSelected: (UsersEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFFF4F4F4),
        title = {
            Text(
                text = "Przypisz kelnera!",
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            if (waiters.isEmpty()) {
                Text("Brak kelnerów w lokalnej bazie.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(waiters) { waiter ->
                        TextButton(
                            onClick = { onWaiterSelected(waiter) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = waiter.username,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

data class TableStatusOption(
    val token: String,
    val name: String
)

private fun buildTableStatusOptions(statuses: List<TableStatusEntity>): List<TableStatusOption> {
    val statusesByToken = statuses.associateBy { it.token.uppercase() }

    return listOf(
        "AVAILABLE" to "Wolny",
        "OCCUPIED" to "Zajęty",
        "RESERVED" to "Zarezerwowany",
        "CLEANING" to "Do sprzątnięcia",
        "OUT_OF_SERVICE" to "Wyłączony"
    ).map { (token, fallbackName) ->
        TableStatusOption(
            token = token,
            name = statusesByToken[token]?.namePl ?: fallbackName
        )
    }
}

private fun normalizeStatusToken(status: String): String {
    return when (status.trim().uppercase()) {
        "AVAILABLE", "WOLNY" -> "AVAILABLE"
        "OCCUPIED", "ZAJĘTY", "ZAJETY" -> "OCCUPIED"
        "RESERVED", "ZAREZERWOWANY", "REZERWACJA" -> "RESERVED"
        "CLEANING", "DO SPRZĄTNIĘCIA", "DO SPRZATNIECIA", "DO SPRZĄTANIA", "DO SPRZATANIA" -> "CLEANING"
        "OUT_OF_SERVICE", "WYŁĄCZONY", "WYLACZONY" -> "OUT_OF_SERVICE"
        else -> status.trim().uppercase()
    }
}
