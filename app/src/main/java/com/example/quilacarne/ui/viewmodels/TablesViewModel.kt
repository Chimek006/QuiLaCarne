package com.example.quilacarne.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.repository.SyncRepository
import com.example.quilacarne.data.repository.TableDisplayStatusLogic
import com.example.quilacarne.data.repository.TableStatusSyncLogic
import com.example.quilacarne.utils.ReservationTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TablesViewModel(private val repository: SyncRepository) : ViewModel() {

    private val _isSyncComplete = MutableStateFlow(true)
    val isSyncComplete: StateFlow<Boolean> = _isSyncComplete
    private var lastTableUiStatesById: Map<UUID, TableUiState> = emptyMap()

    val tables: StateFlow<List<RestaurantTableEntity>> = repository.getTablesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val statuses: StateFlow<List<TableStatusEntity>> = repository.getTableStatusesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val tableUiInput: StateFlow<TableUiInput> = combine(
        tables,
        statuses,
        repository.getReservationsFlow(),
        repository.getOrdersFlow(),
        repository.getUsersFlow()
    ) { tables, statuses, reservations, orders, users ->
        TableUiInput(
            tables = tables,
            statuses = statuses,
            reservations = reservations,
            orders = orders,
            users = users
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TableUiInput()
    )

    val tableUiStates: StateFlow<List<TableUiState>> = combine(
        tableUiInput,
        repository.getOperationalSyncRunningFlow()
    ) { input, isSyncing ->
        val states = buildTableUiStates(input, isSyncing)
        lastTableUiStatesById = states.associateBy { it.id }
        states
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refreshTables() {
        viewModelScope.launch {
            _isSyncComplete.value = false

            try {
                val result = repository.syncOperationalData("tables-manual-refresh")

                result.onSuccess {
                    Log.d("TABLES_SYNC", "Synchronizacja stolikow zakonczona")
                }

                result.onFailure { error ->
                    Log.e("TABLES_SYNC", "Blad sync: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("TABLES_SYNC", "Wyjatek: ${e.message}")
            } finally {
                _isSyncComplete.value = true
            }
        }
    }

    private fun buildTableUiStates(
        input: TableUiInput,
        isSyncing: Boolean
    ): List<TableUiState> {
        val statusesById = input.statuses.associateBy { it.id }
        val usersById = input.users.associateBy { it.id }
        val reservationsByTable = currentOrUpcomingReservationsByTable(input.reservations)
        val ordersByTable = input.orders
            .mapNotNull { order -> order.tableId?.let { tableId -> tableId to order } }
            .groupBy({ it.first }, { it.second })

        return input.tables
            .sortedBy { it.tableNumber }
            .map { table ->
                val previousState = lastTableUiStatesById[table.id]
                val physicalStatusToken = table.statusId
                    ?.let { statusesById[it]?.token }
                    ?.uppercase()
                val activeOrder = ordersByTable[table.id]
                    .orEmpty()
                    .firstOrNull { it.waiterId != null }
                val reservation = reservationsByTable[table.id]
                val displayStatusToken = TableDisplayStatusLogic.resolveDisplayStatusToken(
                    physicalStatusToken = physicalStatusToken,
                    hasActiveOrder = activeOrder != null,
                    hasReservation = reservation != null,
                    previousStatusToken = previousState?.statusToken,
                    isSyncing = isSyncing
                )

                TableUiState(
                    id = table.id,
                    tableNumber = table.tableNumber,
                    statusToken = displayStatusToken,
                    statusNamePl = statusNamePl(displayStatusToken, statusesById.values),
                    statusNameEn = statusNameEn(displayStatusToken, statusesById.values),
                    waiterName = activeOrder
                        ?.takeIf { displayStatusToken == "OCCUPIED" }
                        ?.waiterId
                        ?.let { usersById[it]?.username },
                    reservationTime = reservation
                        ?.takeIf { displayStatusToken == "RESERVED" }
                        ?.let { ReservationTimeUtils.formatTimeRange(it.startTime, it.endTime) }
                )
            }
    }

    private fun currentOrUpcomingReservationsByTable(
        reservations: List<ReservationEntity>
    ): Map<UUID, ReservationEntity> {
        val nowMillis = System.currentTimeMillis()
        return reservations
            .groupBy { it.tableId }
            .mapNotNull { (tableId, tableReservations) ->
                ReservationTimeUtils.selectCurrentOrUpcoming(tableReservations, nowMillis)
                    ?.let { reservation -> tableId to reservation }
            }
            .toMap()
    }

    private fun statusNamePl(token: String, statuses: Collection<TableStatusEntity>): String {
        return statuses
            .firstOrNull { it.token.equals(token, ignoreCase = true) }
            ?.namePl
            ?: TableStatusSyncLogic.tableStatusNamePl(token)
    }

    private fun statusNameEn(token: String, statuses: Collection<TableStatusEntity>): String {
        return statuses
            .firstOrNull { it.token.equals(token, ignoreCase = true) }
            ?.nameEn
            ?: TableStatusSyncLogic.tableStatusNameEn(token)
    }
}

data class TableUiState(
    val id: UUID,
    val tableNumber: Int,
    val statusToken: String,
    val statusNamePl: String,
    val statusNameEn: String,
    val waiterName: String?,
    val reservationTime: String?
)

private data class TableUiInput(
    val tables: List<RestaurantTableEntity> = emptyList(),
    val statuses: List<TableStatusEntity> = emptyList(),
    val reservations: List<ReservationEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val users: List<UsersEntity> = emptyList()
)
