package com.example.quilacarne.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.local.entities.isActiveForTable
import com.example.quilacarne.data.local.relations.OrderItemWithDish
import com.example.quilacarne.data.local.TokenManager
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
import com.example.quilacarne.data.repository.sync.SyncRepository
import com.example.quilacarne.data.repository.sync.TableDisplayStatusLogic
import com.example.quilacarne.utils.ReservationTimeUtils

class TableDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val syncRepository = SyncRepository(db, application.applicationContext)
    private val tokenManager = TokenManager(application.applicationContext)

    private val _orderItems = MutableStateFlow<List<OrderItemWithDish>>(emptyList())
    val orderItems: StateFlow<List<OrderItemWithDish>> = _orderItems

    private val _statusDictionary = MutableStateFlow<List<TableStatusEntity>>(emptyList())
    val statusDictionary: StateFlow<List<TableStatusEntity>> = _statusDictionary

    private val _waiters = MutableStateFlow<List<UsersEntity>>(emptyList())
    val waiters: StateFlow<List<UsersEntity>> = _waiters

    private val _tableStatusId = MutableStateFlow<UUID?>(null)
    val tableStatusId: StateFlow<UUID?> = _tableStatusId

    private val _displayStatusToken = MutableStateFlow<String?>(null)
    val displayStatusToken: StateFlow<String?> = _displayStatusToken

    private val _activeOrderId = MutableStateFlow<UUID?>(null)
    val activeOrderId: StateFlow<UUID?> = _activeOrderId

    private val _assignedWaiterName = MutableStateFlow<String?>(null)
    val assignedWaiterName: StateFlow<String?> = _assignedWaiterName

    private val _reservation = MutableStateFlow<ReservationEntity?>(null)
    val reservation: StateFlow<ReservationEntity?> = _reservation

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var itemsJob: Job? = null
    private var statusesJob: Job? = null
    private var waitersJob: Job? = null
    private var tableJob: Job? = null
    private var ordersJob: Job? = null
    private var remoteSyncJob: Job? = null

    fun loadTableData(tableId: UUID) {
        refreshRemoteOnce()

        waitersJob?.cancel()
        waitersJob = viewModelScope.launch {
            val currentUsername = tokenManager.getCurrentUsername()?.trim().orEmpty()
            ensureCurrentWaiterExists(currentUsername)

            val currentUserFlow = if (currentUsername.isNotBlank()) {
                db.userDao().getUserByUsernameFlow(currentUsername)
            } else {
                MutableStateFlow(null)
            }

            combine(
                db.userDao().getWaitersFlow(),
                currentUserFlow
            ) { waiters, currentUser ->
                val fallbackCurrentUser = currentUser?.takeIf { it.isActive }
                waiters.ifEmpty {
                    listOfNotNull(fallbackCurrentUser)
                }
            }.collect { waiters ->
                _waiters.value = waiters.distinctBy { it.username.trim().lowercase() }
            }
        }

        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val tableSnapshotFlow = combine(
                    db.restaurantTableDao().getTableById(tableId),
                    db.tableStatusDao().getAllStatuses(),
                    db.orderDao().getAllOrders(),
                    db.userDao().getAllUsersFlow(),
                    db.reservationDao().getReservationsForTableFlow(tableId)
                ) { table, statuses, orders, users, reservations ->
                    TableDetailSnapshot(table, statuses, orders, users, reservations)
                }

                combine(
                    tableSnapshotFlow,
                    syncRepository.getOperationalSyncRunningFlow()
                ) { snapshot, isSyncing ->
                    snapshot to isSyncing
                }.collectLatest { (snapshot, isSyncing) ->
                    val table = snapshot.table
                    val statuses = snapshot.statuses
                    val orders = snapshot.orders
                    val users = snapshot.users
                    val reservation = ReservationTimeUtils.selectCurrentOrUpcoming(snapshot.reservations)

                    _statusDictionary.value = statuses

                    val tableStatusToken = statuses
                        .firstOrNull { it.id == table?.statusId }
                        ?.token
                        ?.uppercase()
                    val tableOrders = orders.filter { it.tableId == tableId }
                    val activeOrder = tableOrders.firstOrNull { it.waiterId != null && it.isActiveForTable() }
                    val reservationOrder = tableOrders.firstOrNull()
                    val displayStatusToken = TableDisplayStatusLogic.resolveDisplayStatusToken(
                        physicalStatusToken = tableStatusToken,
                        hasActiveOrder = activeOrder != null,
                        hasReservation = reservation != null,
                        previousStatusToken = _displayStatusToken.value,
                        isSyncing = isSyncing
                    )
                    val occupiedOrder = activeOrder?.takeIf { displayStatusToken == "OCCUPIED" }
                    val orderForItems = occupiedOrder
                        ?: reservationOrder?.takeIf { displayStatusToken == "RESERVED" }

                    _tableStatusId.value = table?.statusId
                    _displayStatusToken.value = displayStatusToken
                    _reservation.value = reservation
                    _activeOrderId.value = occupiedOrder?.id
                    _assignedWaiterName.value = occupiedOrder
                        ?.waiterId
                        ?.let { waiterId ->
                            users.firstOrNull { it.id == waiterId }?.username
                        }

                    if (orderForItems != null) {
                        itemsJob?.cancel()
                        itemsJob = viewModelScope.launch {
                            db.orderDao().getItemsForOrder(orderForItems.id).collect { itemsWithDish ->
                                _orderItems.value = itemsWithDish
                                _isLoading.value = false
                            }
                        }
                    } else {
                        itemsJob?.cancel()
                        _orderItems.value = emptyList()
                        _activeOrderId.value = null
                        if (displayStatusToken != "OCCUPIED") {
                            _assignedWaiterName.value = null
                        }
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
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = syncRepository.changeTableStatusRemote(tableId, token)
                .onFailure { error ->
                    Log.w("TABLE_REMOTE", "Nie udalo sie zapisac statusu stolika w API: ${error.message}")
                }

            onResult(result)
        }
    }

    fun prepareOrderEditForOccupyingTable(
        tableId: UUID,
        onResult: (Result<UUID>) -> Unit
    ) {
        viewModelScope.launch {
            val result = syncRepository.prepareOrderForOccupyingTable(tableId)
                .onFailure { error ->
                    Log.w("TABLE_REMOTE", "Nie udalo sie przygotowac edycji zamowienia: ${error.message}")
                }

            onResult(result)
        }
    }

    private fun refreshRemoteOnce() {
        if (remoteSyncJob?.isActive == true) return

        remoteSyncJob = viewModelScope.launch {
            syncRepository.syncOperationalData("table-detail-load")
                .onFailure { error ->
                    Log.w("TABLE_REMOTE", "Synchronizacja szczegolow nie powiodla sie: ${error.message}")
                }
        }
    }

    private suspend fun ensureCurrentWaiterExists(username: String) {
        if (username.isBlank()) return

        val existing = db.userDao().getUserByUsername(username)
        if (existing != null) {
            if (!existing.role.contains("waiter", ignoreCase = true)) {
                val now = getCurrentTimestamp()
                db.userDao().insertUser(
                    existing.copy(
                        role = "waiter",
                        updatedAt = now
                    )
                )
            }
            return
        }

        Log.w("TABLE_DETAIL", "Current user $username is missing locally; skipping empty-password fallback")
    }

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    override fun onCleared() {
        super.onCleared()
        itemsJob?.cancel()
        statusesJob?.cancel()
        waitersJob?.cancel()
        tableJob?.cancel()
        ordersJob?.cancel()
        remoteSyncJob?.cancel()
    }
}

private data class TableDetailSnapshot(
    val table: RestaurantTableEntity?,
    val statuses: List<TableStatusEntity>,
    val orders: List<OrderEntity>,
    val users: List<UsersEntity>,
    val reservations: List<ReservationEntity>
)
