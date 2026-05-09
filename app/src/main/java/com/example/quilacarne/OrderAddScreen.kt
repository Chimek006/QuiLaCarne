package com.example.quilacarne

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.ui.QuiLaCarneHeader
import com.example.quilacarne.ui.theme.green
import com.example.quilacarne.ui.theme.lightGray
import com.example.quilacarne.ui.theme.orange
import com.example.quilacarne.ui.viewmodels.OrderAddViewModel
import com.example.quilacarne.ui.viewmodels.PendingOrderItem
import java.util.Locale
import java.util.UUID

@Composable
fun OrderAddScreen(
    navController: NavController,
    tableId: UUID,
    orderId: UUID,
    viewModel: OrderAddViewModel = viewModel()
) {
    val table by viewModel.table.collectAsState()
    val dishes by viewModel.dishes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val pendingItems by viewModel.pendingItems.collectAsState()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(tableId, orderId) {
        viewModel.load(tableId, orderId)
    }

    val filteredDishes = remember(dishes, searchQuery) {
        dishes
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }

    val quantitiesByDish = remember(pendingItems) {
        pendingItems.mapNotNull { item ->
            item.dishId?.let { dishId -> dishId to item.quantity }
        }.toMap()
    }

    val totalPrice = remember(pendingItems) {
        pendingItems.sumOf { it.priceAtTimeOfOrder * it.quantity }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        QuiLaCarneHeader(navController = navController, showBack = true)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Dodaj pozycje",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = table?.let { "Stolik ${it.tableNumber}" } ?: "Stolik",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Szukaj dania...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = green,
                        unfocusedBorderColor = lightGray,
                        cursorColor = green
                    ),
                    singleLine = true
                )
            }

            item {
                CategorySelector(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onSelected = viewModel::selectCategory
                )
            }

            item {
                CurrentOrderCard(
                    items = pendingItems,
                    totalPrice = totalPrice,
                    onDecrease = viewModel::decreaseItem,
                    onRemove = viewModel::removeItem
                )
            }

            item {
                SaveChangesButton(
                    enabled = hasPendingChanges && !isSaving,
                    isSaving = isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        isSaving = true
                        viewModel.saveChanges {
                            isSaving = false
                        }
                    }
                )
            }

            if (filteredDishes.isEmpty()) {
                item {
                    Text(
                        text = "Brak dań",
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(filteredDishes) { dish ->
                    DishAddRow(
                        dish = dish,
                        quantity = quantitiesByDish[dish.id] ?: 0,
                        onAdd = { viewModel.addDish(dish) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelector(
    categories: List<DishCategoryEntity>,
    selectedCategoryId: UUID?,
    onSelected: (UUID?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OrderCategoryChip(
                name = "Wszystkie",
                isSelected = selectedCategoryId == null,
                onClick = { onSelected(null) }
            )
        }
        items(categories) { category ->
            OrderCategoryChip(
                name = category.namePl,
                isSelected = selectedCategoryId == category.id,
                onClick = { onSelected(category.id) }
            )
        }
    }
}

@Composable
private fun OrderCategoryChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) green else lightGray,
        contentColor = if (isSelected) Color.White else Color.Black
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CurrentOrderCard(
    items: List<PendingOrderItem>,
    totalPrice: Int,
    onDecrease: (PendingOrderItem) -> Unit,
    onRemove: (PendingOrderItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zamówienie", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(formatPrice(totalPrice), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }

            if (items.isEmpty()) {
                Text("Brak pozycji w zamówieniu", color = Color.Gray)
            } else {
                items.forEach { wrapper ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wrapper.dish?.name ?: "Danie nieznane",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            Text(
                                text = "${wrapper.quantity} x ${formatPrice(wrapper.priceAtTimeOfOrder)}",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { onDecrease(wrapper) }) {
                            Text("-", color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onRemove(wrapper) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishAddRow(
    dish: DishEntity,
    quantity: Int,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = dish.imageUrl,
                contentDescription = dish.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(lightGray),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(android.R.drawable.ic_menu_report_image),
                error = painterResource(android.R.drawable.ic_menu_report_image)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dish.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = formatPrice(dish.price),
                    fontSize = 15.sp,
                    color = orange,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (quantity > 0) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(green.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(quantity.toString(), color = green, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(green)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            }
        }
    }
}

private fun formatPrice(price: Int): String {
    return "${String.format(Locale.US, "%.2f", price / 100.0)} zł"
}
