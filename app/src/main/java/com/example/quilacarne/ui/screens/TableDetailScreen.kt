package com.example.quilacarne.ui.screens

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
import com.example.quilacarne.ui.i18n.AppLanguage
import com.example.quilacarne.ui.i18n.localizedName
import com.example.quilacarne.ui.i18n.rememberAppLanguage
import com.example.quilacarne.ui.theme.*
import com.example.quilacarne.ui.viewmodels.TableDetailViewModel
import com.example.quilacarne.utils.ReservationTimeUtils
import java.util.UUID
import java.util.Locale
import com.example.quilacarne.ui.components.QuiLaCarneHeader

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
    val displayStatusToken by viewModel.displayStatusToken.collectAsState()
    val waiters by viewModel.waiters.collectAsState()
    val activeOrderId by viewModel.activeOrderId.collectAsState()
    val assignedWaiterName by viewModel.assignedWaiterName.collectAsState()
    val reservation by viewModel.reservation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val language = rememberAppLanguage()
    var showStatusDialog by remember { mutableStateOf(false) }
    var showWaiterDialog by remember { mutableStateOf(false) }
    var pendingOccupiedStatus by remember { mutableStateOf<TableStatusOption?>(null) }
    var stagedStatus by remember(tableId) { mutableStateOf<TableStatusOption?>(null) }
    var stagedWaiter by remember(tableId) { mutableStateOf<UsersEntity?>(null) }
    var isSavingChanges by remember { mutableStateOf(false) }
    var statusSaveError by remember(tableId) { mutableStateOf<String?>(null) }

    LaunchedEffect(tableId) {
        viewModel.loadTableData(tableId)
    }

    val statusOptions = remember(statusDict, language) {
        buildTableStatusOptions(statusDict, language)
    }

    val currentToken = displayStatusToken ?: normalizeStatusToken(tableStatus)
    val displayedToken = stagedStatus?.token ?: currentToken
    val statusInfo = statusOptions.find { it.token == displayedToken }

    val displayStatusName = statusInfo?.name ?: when (displayedToken) {
        "AVAILABLE" -> language.choose("Wolny", "Available")
        "OCCUPIED" -> language.choose("Zajety", "Occupied")
        "RESERVED" -> language.choose("Zarezerwowany", "Reserved")
        "CLEANING" -> language.choose("Do sprzatniecia", "Cleaning")
        "OUT_OF_SERVICE" -> language.choose("Wylaczony", "Out of service")
        else -> displayedToken.lowercase().replaceFirstChar { it.uppercase() }
    }

    val (statusPillColor, statusTextColor) = getStatusColorsByToken(displayedToken)
    val displayedWaiterName = stagedWaiter?.username ?: assignedWaiterName
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

            if (displayedToken == "OCCUPIED") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${language.choose("Kelner", "Waiter")}: ${displayedWaiterName ?: language.choose("brak", "none")}",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.DarkGray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (displayedToken == "RESERVED") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${language.choose("Godziny rezerwacji", "Reservation time")}: ${
                        reservation?.let { ReservationTimeUtils.formatTimeRange(it.startTime, it.endTime) }.orEmpty()
                    }",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.DarkGray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            ActionButtons(
                canEditOrder = activeOrderId != null,
                language = language,
                onStatusClick = { showStatusDialog = true },
                onEditOrder = {
                    activeOrderId?.let { orderId ->
                        navController.navigate("order_add/$tableId/$orderId")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = language.choose("Zamowiono:", "Ordered:"),
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
                            text = when {
                                displayedToken == "RESERVED" -> language.choose(
                                    "Brak zamowionych dan dla tej rezerwacji",
                                    "No ordered dishes for this reservation"
                                )
                                displayedToken == "AVAILABLE" -> language.choose("Brak pozycji - stolik wolny", "No items - table available")
                                else -> language.choose("Brak aktywnych zamowien", "No active orders")
                            },
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    } else {
                        orderItems.forEachIndexed { index, wrapper ->
                            OrderItemRow(
                                name = wrapper.dish?.name ?: language.choose("Danie nieznane", "Unknown dish"),
                                quantity = wrapper.item.quantity,
                                note = wrapper.item.note,
                                status = language.choose("W kuchni", "In kitchen"),
                                price = wrapper.item.priceAtTimeOfOrder.toDouble(),
                                language = language,
                                accentColor = Color.Gray
                            )
                            if (index < orderItems.size - 1) {
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            PriceSummary(totalPrice, language)

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { navController.navigate("report_client/$tableId") },
                colors = ButtonDefaults.buttonColors(containerColor = orange),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(language.choose("Zglos klienta", "Report client"), fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SaveChangesButton(
                enabled = hasPendingChanges && !isSavingChanges,
                isSaving = isSavingChanges,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = saveClick@{
                    val status = stagedStatus ?: return@saveClick
                    if (status.token == currentToken) {
                        stagedStatus = null
                        stagedWaiter = null
                        statusSaveError = null
                        return@saveClick
                    }
                    statusTransitionError(
                        currentToken = currentToken,
                        targetToken = status.token,
                        language = language,
                        hasReservation = reservation != null
                    )?.let { message ->
                        statusSaveError = message
                        stagedStatus = null
                        stagedWaiter = null
                        return@saveClick
                    }
                    isSavingChanges = true
                    statusSaveError = null

                    if (status.token == "OCCUPIED") {
                        val waiter = stagedWaiter
                        if (waiter != null) {
                            viewModel.assignWaiterAndOccupyTable(
                                tableId = tableId,
                                statusToken = status.token,
                                statusName = status.name,
                                waiterId = waiter.id
                            ) { result ->
                                isSavingChanges = false
                                result
                                    .onSuccess { orderId ->
                                        stagedStatus = null
                                        stagedWaiter = null
                                        statusSaveError = null
                                        navController.navigate("order_add/$tableId/$orderId")
                                    }
                                    .onFailure { error ->
                                        stagedStatus = null
                                        stagedWaiter = null
                                        statusSaveError = error.message ?: language.choose("Nie udalo sie zajac stolika.", "Could not occupy the table.")
                                    }
                            }
                        } else {
                            isSavingChanges = false
                            statusSaveError = language.choose("Wybierz kelnera przed zajeciem stolika.", "Select a waiter before occupying the table.")
                        }
                    } else {
                        viewModel.changeTableStatus(
                            tableId = tableId,
                            token = status.token,
                            name = status.name
                        ) { result ->
                            isSavingChanges = false
                            result
                                .onSuccess {
                                    stagedStatus = null
                                    stagedWaiter = null
                                    statusSaveError = null
                                }
                                .onFailure { error ->
                                    stagedStatus = null
                                    stagedWaiter = null
                                    statusSaveError = error.message ?: language.choose("Nie udalo sie zapisac statusu stolika.", "Could not save table status.")
                                }
                        }
                    }
                }
            )
            statusSaveError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showStatusDialog) {
            StatusSelectionDialog(
                statusOptions = statusOptions,
                currentStatusToken = displayedToken,
                language = language,
                onDismiss = { showStatusDialog = false },
                onStatusSelected = statusSelected@ { status ->
                    showStatusDialog = false
                    statusSaveError = null
                    if (status.token == currentToken) {
                        stagedStatus = null
                        stagedWaiter = null
                        return@statusSelected
                    }
                    statusTransitionError(
                        currentToken = currentToken,
                        targetToken = status.token,
                        language = language,
                        hasReservation = reservation != null
                    )?.let { message ->
                        statusSaveError = message
                        stagedStatus = null
                        stagedWaiter = null
                        return@statusSelected
                    }
                    if (status.token == "OCCUPIED") {
                        pendingOccupiedStatus = status
                        showWaiterDialog = true
                    } else {
                        stagedStatus = status
                        stagedWaiter = null
                    }
                }
            )
        }

        if (showWaiterDialog) {
            WaiterAssignmentDialog(
                waiters = waiters,
                language = language,
                onDismiss = {
                    showWaiterDialog = false
                    pendingOccupiedStatus = null
                },
                onWaiterSelected = { waiter ->
                    val status = pendingOccupiedStatus
                    if (status != null) {
                        statusSaveError = null
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
fun OrderItemRow(
    name: String,
    quantity: Int,
    note: String?,
    status: String,
    price: Double,
    language: AppLanguage,
    accentColor: Color
) {
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
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$name x$quantity", fontSize = 15.sp, textAlign = TextAlign.Center)
            note?.takeIf { it.isNotBlank() }?.let { itemNote ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${language.choose("Notatka", "Note")}: $itemNote",
                    fontSize = 12.sp,
                    color = green,
                    textAlign = TextAlign.Center
                )
            }
        }

        val formattedPrice = String.format(Locale.US, "%.2f", (price * quantity) / 100.0)
        val currency = language.choose("zl", "PLN")
        Text(
            text = "$formattedPrice $currency",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun PriceSummary(totalPrice: Double, language: AppLanguage) {
    Spacer(modifier = Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(language.choose("Cena laczna:", "Total:"), fontSize = 20.sp, fontWeight = FontWeight.Light)

        val formattedTotal = String.format(Locale.US, "%.2f", totalPrice / 100.0)
        Text(text = "$formattedTotal ${language.choose("zl", "PLN")}", fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun SaveChangesButton(
    enabled: Boolean,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val language = rememberAppLanguage()
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
            Text(language.choose("Zapisz", "Save"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionButtons(
    canEditOrder: Boolean,
    language: AppLanguage,
    onStatusClick: () -> Unit,
    onEditOrder: () -> Unit
) {
    Column {
        Button(
            onClick = onStatusClick,
            colors = ButtonDefaults.buttonColors(containerColor = green),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(language.choose("Zmien status stolika", "Change table status"), color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onEditOrder,
            enabled = canEditOrder,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(language.choose("Edytuj zamowienie", "Edit order"), color = Color.Black)
        }
    }
}

@Composable
private fun StatusSelectionDialog(
    statusOptions: List<TableStatusOption>,
    currentStatusToken: String,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onStatusSelected: (TableStatusOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFFF4F4F4),
        title = {
            Text(
                text = language.choose("Zmien status stolika", "Change table status"),
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(statusOptions) { status ->
                    val selected = status.token == currentStatusToken
                    DialogOptionButton(
                        text = if (selected) "${status.name} ✓" else status.name,
                        selected = selected,
                        onClick = { onStatusSelected(status) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(language.choose("Anuluj", "Cancel"))
            }
        }
    )
}

@Composable
private fun WaiterAssignmentDialog(
    waiters: List<UsersEntity>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onWaiterSelected: (UsersEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFFF4F4F4),
        title = {
            Text(
                text = language.choose("Przypisz kelnera", "Assign waiter"),
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            if (waiters.isEmpty()) {
                Text(language.choose("Brak kelnerow w lokalnej bazie.", "No waiters in the local database."), color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(waiters) { waiter ->
                        DialogOptionButton(
                            text = waiter.username,
                            onClick = { onWaiterSelected(waiter) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(language.choose("Anuluj", "Cancel"))
            }
        }
    )
}

@Composable
private fun DialogOptionButton(
    text: String,
    supportingText: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val borderColor = when {
        selected -> green
        enabled -> Color(0xFFBDBDBD)
        else -> Color(0xFFDADADA)
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) green.copy(alpha = 0.12f) else Color.White,
            disabledContainerColor = Color(0xFFEFEFEF),
            contentColor = Color.Black,
            disabledContentColor = Color.Gray
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                color = if (enabled) Color.Black else Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            supportingText?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    color = if (enabled) Color.Gray else Color(0xFF8E8E8E),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class TableStatusOption(
    val token: String,
    val name: String
)

private fun buildTableStatusOptions(statuses: List<TableStatusEntity>, language: AppLanguage): List<TableStatusOption> {
    val statusesByToken = statuses.associateBy { it.token.uppercase() }

    return listOf(
        "AVAILABLE" to language.choose("Wolny", "Available"),
        "OCCUPIED" to language.choose("Zajety", "Occupied"),
        "CLEANING" to language.choose("Do sprzatniecia", "Cleaning"),
        "OUT_OF_SERVICE" to language.choose("Wylaczony", "Out of service")
    ).map { (token, fallbackName) ->
        TableStatusOption(
            token = token,
            name = statusesByToken[token]?.localizedName(language) ?: fallbackName
        )
    }
}

private fun statusTransitionError(
    currentToken: String,
    targetToken: String,
    language: AppLanguage,
    hasReservation: Boolean
): String? {
    val current = currentToken.uppercase()
    val target = targetToken.uppercase()

    if (current == target) return null

    return when (current) {
        "OCCUPIED" -> when (target) {
            "CLEANING" -> null
            "AVAILABLE" -> language.choose(
                "Najpierw ustaw stolik jako do sprzatniecia, potem jako wolny.",
                "Set the table to cleaning first, then available."
            )
            else -> language.choose(
                "Zajety stolik mozna teraz przelaczyc tylko na do sprzatniecia.",
                "An occupied table can only be changed to cleaning now."
            )
        }
        "CLEANING" -> if (target == "AVAILABLE") {
            null
        } else {
            language.choose(
                "Stolik do sprzatniecia mozna przelaczyc tylko na wolny.",
                "A cleaning table can only be changed to available."
            )
        }
        "OUT_OF_SERVICE" -> if (target == "AVAILABLE") {
            null
        } else {
            language.choose(
                "Wylaczony stolik mozna przelaczyc tylko na wolny.",
                "An out-of-service table can only be changed to available."
            )
        }
        "AVAILABLE" -> when (target) {
            "OUT_OF_SERVICE" -> null
            "OCCUPIED" -> if (hasReservation) {
                null
            } else {
                language.choose(
                    "Nie mozna zajac stolika bez aktualnej lub nadchodzacej rezerwacji.",
                    "You cannot occupy a table without a current or upcoming reservation."
                )
            }
            "CLEANING" -> language.choose(
                "Wolny stolik nie wymaga sprzatania.",
                "An available table does not require cleaning."
            )
            else -> language.choose(
                "Ten status nie jest dostepny dla aktualnego stanu stolika.",
                "This status is not available for the current table state."
            )
        }
        "RESERVED" -> if (target == "OCCUPIED" && !hasReservation) {
            language.choose(
                "Nie mozna zajac stolika bez aktualnej lub nadchodzacej rezerwacji.",
                "You cannot occupy a table without a current or upcoming reservation."
            )
        } else if (target == "OUT_OF_SERVICE" || target == "OCCUPIED") {
            null
        } else {
            language.choose(
                "Ten status nie jest dostepny dla aktualnego stanu stolika.",
                "This status is not available for the current table state."
            )
        }
        else -> null
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
