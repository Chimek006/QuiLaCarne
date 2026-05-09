package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.relations.OrderItemWithDish
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.quilacarne.data.local.AppDatabase

class TableDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _orderItems = MutableStateFlow<List<OrderItemWithDish>>(emptyList())
    val orderItems: StateFlow<List<OrderItemWithDish>> = _orderItems

    private val _statusDictionary = MutableStateFlow<List<TableStatusEntity>>(emptyList())
    val statusDictionary: StateFlow<List<TableStatusEntity>> = _statusDictionary

    private val _waiters = MutableStateFlow<List<UsersEntity>>(emptyList())
    val waiters: StateFlow<List<UsersEntity>> = _waiters

    private val _tables = MutableStateFlow<List<RestaurantTableEntity>>(emptyList())
    val tables: StateFlow<List<RestaurantTableEntity>> = _tables

    private val _tableStatusId = MutableStateFlow<UUID?>(null)
    val tableStatusId: StateFlow<UUID?> = _tableStatusId

    private val _activeOrderId = MutableStateFlow<UUID?>(null)
    val activeOrderId: StateFlow<UUID?> = _activeOrderId

    private val _assignedWaiterName = MutableStateFlow<String?>(null)
    val assignedWaiterName: StateFlow<String?> = _assignedWaiterName

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var itemsJob: Job? = null
    private var statusesJob: Job? = null
    private var waitersJob: Job? = null
    private var tableJob: Job? = null
    private var tablesJob: Job? = null
    private var ordersJob: Job? = null

    fun loadTableData(tableId: UUID) {
        waitersJob?.cancel()
        waitersJob = viewModelScope.launch {
            combine(
                db.userDao().getWaitersFlow(),
                db.userDao().getAllUsersFlow()
            ) { waiters, users ->
                waiters.ifEmpty { users }
            }.collect { users ->
                _waiters.value = users.distinctBy { it.username.trim().lowercase() }
            }
        }

        tablesJob?.cancel()
        tablesJob = viewModelScope.launch {
            db.restaurantTableDao().getAllTablesFlow().collect { tables ->
                _tables.value = tables
            }
        }

        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                combine(
                    db.restaurantTableDao().getTableById(tableId),
                    db.tableStatusDao().getAllStatuses(),
                    db.orderDao().getAllOrders(),
                    db.userDao().getAllUsersFlow()
                ) { table, statuses, orders, users ->
                    TableDetailSnapshot(table, statuses, orders, users)
                }.collectLatest { snapshot ->
                    val table = snapshot.table
                    val statuses = snapshot.statuses
                    val orders = snapshot.orders
                    val users = snapshot.users

                    _statusDictionary.value = statuses
                    _tableStatusId.value = table?.statusId

                    val tableStatusToken = getTableStatusToken(table, statuses)
                    val activeOrder = if (tableStatusToken == "OCCUPIED") {
                        orders.find { it.tableId == tableId }
                    } else {
                        null
                    }
                    _activeOrderId.value = activeOrder?.id
                    _assignedWaiterName.value = activeOrder
                        ?.waiterId
                        ?.let { waiterId ->
                            users.firstOrNull { it.id == waiterId }?.username
                        }

                    if (activeOrder != null) {
                        itemsJob?.cancel()
                        itemsJob = viewModelScope.launch {
                            db.orderDao().getItemsForOrder(activeOrder.id).collect { itemsWithDish ->
                                _orderItems.value = itemsWithDish
                                _isLoading.value = false
                            }
                        }
                    } else {
                        itemsJob?.cancel()
                        _orderItems.value = emptyList()
                        _activeOrderId.value = null
                        _assignedWaiterName.value = null
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                e.printStackTrace()
            }
        }
    }

    fun changeTableStatus(
        tableId: UUID,
        token: String,
        name: String,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val statusId = ensureStatus(token, name)
            db.restaurantTableDao().updateStatus(tableId, statusId, getCurrentTimestamp())
            onSaved()
        }
    }

    fun assignWaiterAndOccupyTable(
        tableId: UUID,
        statusToken: String,
        statusName: String,
        waiterId: UUID,
        onOrderReady: (UUID) -> Unit
    ) {
        viewModelScope.launch {
            val now = getCurrentTimestamp()
            val statusId = ensureStatus(statusToken, statusName)
            val orderId = db.withTransaction {
                db.restaurantTableDao().updateStatus(tableId, statusId, now)

                val activeOrder = db.orderDao().getActiveOrderForTableOnce(tableId)

                if (activeOrder != null) {
                    db.orderDao().updateOrderWaiter(activeOrder.id, waiterId, now)
                    activeOrder.id
                } else {
                    val newOrder = OrderEntity(
                        id = UUID.randomUUID(),
                        tableId = tableId,
                        waiterId = waiterId,
                        statusId = null,
                        totalPrice = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                    db.orderDao().insertOrder(newOrder)
                    newOrder.id
                }
            }
            onOrderReady(orderId)
        }
    }

    fun moveTableOrder(
        currentTableId: UUID,
        newTableId: UUID,
        onMoved: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = getCurrentTimestamp()
            val availableStatusId = ensureStatus("AVAILABLE", "Wolny")
            val occupiedStatusId = ensureStatus("OCCUPIED", "Zajęty")

            val movedOrdersCount = db.withTransaction {
                val statuses = db.tableStatusDao().getAllStatusesOnce()
                val targetTable = db.restaurantTableDao().getTableByIdOnce(newTableId)
                val targetStatusToken = getTableStatusToken(targetTable, statuses)
                val currentOrder = db.orderDao().getActiveOrderForTableOnce(currentTableId)

                if (targetStatusToken != "AVAILABLE" || currentOrder == null) {
                    0
                } else {
                    val movedCount = db.orderDao().moveOrderToTable(
                        orderId = currentOrder.id,
                        newTableId = newTableId,
                        updatedAt = now
                    )

                    if (movedCount > 0) {
                        db.restaurantTableDao().updateStatus(
                            currentTableId,
                            availableStatusId,
                            now
                        )
                        db.restaurantTableDao().updateStatus(
                            newTableId,
                            occupiedStatusId,
                            now
                        )
                    }

                    movedCount
                }
            }

            onMoved(movedOrdersCount > 0)
        }
    }

    private suspend fun ensureStatus(token: String, name: String): UUID {
        val statusId = token.toStableUUID()
        val now = getCurrentTimestamp()
        db.tableStatusDao().insertAll(
            listOf(
                TableStatusEntity(
                    id = statusId,
                    token = token,
                    namePl = name,
                    nameEn = name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        return statusId
    }

    private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray())

    private fun getTableStatusToken(
        table: RestaurantTableEntity?,
        statuses: List<TableStatusEntity>
    ): String {
        return statuses
            .find { it.id == table?.statusId }
            ?.token
            ?.uppercase()
            ?: "AVAILABLE"
    }

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    override fun onCleared() {
        super.onCleared()
        itemsJob?.cancel()
        statusesJob?.cancel()
        waitersJob?.cancel()
        tableJob?.cancel()
        tablesJob?.cancel()
        ordersJob?.cancel()
    }
}

private data class TableDetailSnapshot(
    val table: RestaurantTableEntity?,
    val statuses: List<TableStatusEntity>,
    val orders: List<OrderEntity>,
    val users: List<UsersEntity>
)
