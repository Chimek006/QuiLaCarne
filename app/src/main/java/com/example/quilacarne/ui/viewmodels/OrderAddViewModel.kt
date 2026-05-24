package com.example.quilacarne.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.relations.OrderItemWithDish
import com.example.quilacarne.data.repository.sync.SyncRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class OrderAddViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val syncRepository = SyncRepository(db, application.applicationContext)

    private val _selectedCategoryId = MutableStateFlow<UUID?>(null)
    val selectedCategoryId: StateFlow<UUID?> = _selectedCategoryId

    private val _orderItems = MutableStateFlow<List<OrderItemWithDish>>(emptyList())
    val orderItems: StateFlow<List<OrderItemWithDish>> = _orderItems

    private val _pendingItems = MutableStateFlow<List<PendingOrderItem>>(emptyList())
    val pendingItems: StateFlow<List<PendingOrderItem>> = _pendingItems

    private val _hasPendingChanges = MutableStateFlow(false)
    val hasPendingChanges: StateFlow<Boolean> = _hasPendingChanges

    private val _table = MutableStateFlow<RestaurantTableEntity?>(null)
    val table: StateFlow<RestaurantTableEntity?> = _table

    private val _pendingWaiterId = MutableStateFlow<UUID?>(null)
    val pendingWaiterId: StateFlow<UUID?> = _pendingWaiterId

    private val _pendingWaiterName = MutableStateFlow<String?>(null)
    val pendingWaiterName: StateFlow<String?> = _pendingWaiterName

    private var orderId: UUID? = null
    private var orderItemsJob: Job? = null
    private var tableJob: Job? = null

    val categories: StateFlow<List<DishCategoryEntity>> = db.dishCategoryDao()
        .getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dishes: StateFlow<List<DishEntity>> = combine(
        db.dishDao().getAvailableDishes(),
        _selectedCategoryId
    ) { allDishes, selectedId ->
        if (selectedId == null) {
            allDishes
        } else {
            allDishes.filter { it.categoryId == selectedId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(tableId: UUID, orderId: UUID, pendingWaiterId: UUID? = null) {
        this.orderId = orderId
        _pendingWaiterId.value = pendingWaiterId
        _pendingWaiterName.value = null
        _hasPendingChanges.value = false

        if (pendingWaiterId != null) {
            viewModelScope.launch {
                _pendingWaiterName.value = db.userDao()
                    .getAllUsersOnce()
                    .firstOrNull { it.id == pendingWaiterId }
                    ?.username
            }
        }

        orderItemsJob?.cancel()
        orderItemsJob = viewModelScope.launch {
            db.orderDao().getItemsForOrder(orderId).collect { items ->
                _orderItems.value = items
                if (!_hasPendingChanges.value) {
                    _pendingItems.value = items.toPendingItems()
                }
            }
        }

        tableJob?.cancel()
        tableJob = viewModelScope.launch {
            db.restaurantTableDao().getTableById(tableId).collect { table ->
                _table.value = table
            }
        }
    }

    fun selectCategory(categoryId: UUID?) {
        _selectedCategoryId.value = categoryId
    }

    fun addDish(dish: DishEntity) {
        val currentItems = _pendingItems.value.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.dishId == dish.id }

        if (existingIndex >= 0) {
            val existing = currentItems[existingIndex]
            currentItems[existingIndex] = existing.copy(quantity = existing.quantity + 1)
        } else {
            currentItems.add(
                PendingOrderItem(
                    sourceItemId = null,
                    dishId = dish.id,
                    dish = dish,
                    quantity = 1,
                    priceAtTimeOfOrder = dish.price,
                    note = null
                )
            )
        }

        _pendingItems.value = currentItems
        _hasPendingChanges.value = true
    }

    fun decreaseItem(item: PendingOrderItem) {
        val currentItems = _pendingItems.value.toMutableList()
        val index = currentItems.indexOfFirst {
            it.sourceItemId == item.sourceItemId && it.dishId == item.dishId
        }

        if (index < 0) return

        val existing = currentItems[index]
        if (existing.quantity <= 1) {
            currentItems.removeAt(index)
        } else {
            currentItems[index] = existing.copy(quantity = existing.quantity - 1)
        }

        _pendingItems.value = currentItems
        _hasPendingChanges.value = true
    }

    fun removeItem(item: PendingOrderItem) {
        _pendingItems.value = _pendingItems.value.filterNot {
            it.sourceItemId == item.sourceItemId && it.dishId == item.dishId
        }
        _hasPendingChanges.value = true
    }

    fun saveChanges(onSaved: (Boolean, String?) -> Unit = { _, _ -> }) {
        val currentOrderId = orderId ?: return
        val pendingItems = _pendingItems.value
        val waiterIdToAssign = _pendingWaiterId.value

        viewModelScope.launch {
            val now = getCurrentTimestamp()
            val existingItemsBeforeSave = db.orderDao().getItemsForOrderOnce(currentOrderId)

            val remoteResult = saveRemoteChanges(
                orderId = currentOrderId,
                existingItems = existingItemsBeforeSave,
                pendingItems = pendingItems,
                pendingWaiterId = waiterIdToAssign
            )
            if (remoteResult.isFailure) {
                onSaved(false, remoteResult.exceptionOrNull()?.message)
                return@launch
            }

            db.withTransaction {
                val existingItems = db.orderDao().getItemsForOrderOnce(currentOrderId)
                val existingByDish = existingItems
                    .mapNotNull { item ->
                        item.productId?.let { dishId -> dishId to item }
                    }
                    .toMap()
                val savedIds = mutableSetOf<UUID>()

                pendingItems.forEach { pending ->
                    val dishId = pending.dishId
                    val sourceItemId = pending.sourceItemId
                    val existingItem = if (dishId != null) {
                        existingByDish[dishId]
                    } else {
                        existingItems.find { it.id == sourceItemId }
                    }

                    if (existingItem != null) {
                        db.orderDao().updateOrderItemQuantity(
                            itemId = existingItem.id,
                            quantity = pending.quantity,
                            updatedAt = now
                        )
                        savedIds.add(existingItem.id)
                    } else if (dishId != null) {
                        val newItem = OrderItemEntity(
                            id = UUID.randomUUID(),
                            orderId = currentOrderId,
                            productId = dishId,
                            quantity = pending.quantity,
                            priceAtTimeOfOrder = pending.priceAtTimeOfOrder,
                            note = pending.note,
                            createdAt = now,
                            updatedAt = now
                        )
                        db.orderDao().insertOrderItem(newItem)
                        savedIds.add(newItem.id)
                    }
                }

                existingItems
                    .filter { it.id !in savedIds }
                    .forEach { item ->
                        db.orderDao().deleteOrderItem(item.id)
                    }

                refreshOrderTotal(currentOrderId, now)
                if (waiterIdToAssign != null) {
                    db.orderDao().updateOrderWaiter(currentOrderId, waiterIdToAssign, now)
                }
            }

            _hasPendingChanges.value = false
            if (waiterIdToAssign != null) {
                _pendingWaiterId.value = null
                _pendingWaiterName.value = null
            }
            onSaved(true, null)
        }
    }

    private suspend fun refreshOrderTotal(orderId: UUID, now: String) {
        val total = db.orderDao()
            .getItemsForOrderOnce(orderId)
            .sumOf { it.priceAtTimeOfOrder * it.quantity }

        db.orderDao().updateOrderTotal(orderId, total, now)
    }

    private suspend fun saveRemoteChanges(
        orderId: UUID,
        existingItems: List<OrderItemEntity>,
        pendingItems: List<PendingOrderItem>,
        pendingWaiterId: UUID?
    ): Result<Unit> {
        val existingByDish = existingItems
            .mapNotNull { item -> item.productId?.let { dishId -> dishId to item.quantity } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, quantities) -> quantities.sum() }

        val pendingByDish = pendingItems
            .mapNotNull { item -> item.dishId?.let { dishId -> dishId to item.quantity } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, quantities) -> quantities.sum() }

        val dishIds = existingByDish.keys + pendingByDish.keys
        val additions = mutableMapOf<UUID, Int>()
        val removals = mutableMapOf<UUID, Int>()

        dishIds.forEach { dishId ->
            val existingQuantity = existingByDish[dishId] ?: 0
            val pendingQuantity = pendingByDish[dishId] ?: 0
            val delta = pendingQuantity - existingQuantity

            when {
                delta > 0 -> additions[dishId] = delta
                delta < 0 -> removals[dishId] = -delta
            }
        }

        var saveResult = Result.success(Unit)

        if (additions.isNotEmpty() || removals.isNotEmpty()) {
            saveResult = syncRepository.saveReservationItemDeltas(
                orderId = orderId,
                additions = additions,
                removals = removals
            ).onFailure { error ->
                Log.w("ORDER_REMOTE", "Nie udalo sie zapisac pozycji zamowienia w API: ${error.message}")
            }

        }

        if (saveResult.isSuccess && pendingWaiterId != null) {
            saveResult = syncRepository.assignWaiterToReservationForOrder(orderId)
                .onFailure { error ->
                    Log.w("ORDER_REMOTE", "Nie udalo sie przypisac kelnera w API: ${error.message}")
                }
        }

        return saveResult
    }

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    private fun List<OrderItemWithDish>.toPendingItems(): List<PendingOrderItem> {
        return map { wrapper ->
            PendingOrderItem(
                sourceItemId = wrapper.item.id,
                dishId = wrapper.item.productId,
                dish = wrapper.dish,
                quantity = wrapper.item.quantity,
                priceAtTimeOfOrder = wrapper.item.priceAtTimeOfOrder,
                note = wrapper.item.note
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        orderItemsJob?.cancel()
        tableJob?.cancel()
    }
}

data class PendingOrderItem(
    val sourceItemId: UUID?,
    val dishId: UUID?,
    val dish: DishEntity?,
    val quantity: Int,
    val priceAtTimeOfOrder: Int,
    val note: String? = null
)
