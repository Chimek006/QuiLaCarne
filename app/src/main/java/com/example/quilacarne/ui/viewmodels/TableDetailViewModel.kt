package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.local.entities.OrderEntity
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

    private val _tableStatusId = MutableStateFlow<UUID?>(null)
    val tableStatusId: StateFlow<UUID?> = _tableStatusId

    private val _activeOrderId = MutableStateFlow<UUID?>(null)
    val activeOrderId: StateFlow<UUID?> = _activeOrderId

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var itemsJob: Job? = null
    private var statusesJob: Job? = null
    private var waitersJob: Job? = null
    private var tableJob: Job? = null
    private var ordersJob: Job? = null

    fun loadTableData(tableId: UUID) {
        statusesJob?.cancel()
        statusesJob = viewModelScope.launch {
            db.tableStatusDao().getAllStatuses().collect { statuses ->
                _statusDictionary.value = statuses
            }
        }

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

        tableJob?.cancel()
        tableJob = viewModelScope.launch {
            db.restaurantTableDao().getTableById(tableId).collect { table ->
                _tableStatusId.value = table?.statusId
            }
        }

        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                db.orderDao().getAllOrders().collectLatest { orders ->
                    val activeOrder = orders.find { it.tableId == tableId }
                    _activeOrderId.value = activeOrder?.id

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

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    override fun onCleared() {
        super.onCleared()
        itemsJob?.cancel()
        statusesJob?.cancel()
        waitersJob?.cancel()
        tableJob?.cancel()
        ordersJob?.cancel()
    }
}
