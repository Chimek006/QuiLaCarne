package com.example.quilacarne.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.repository.SyncRepository
import com.example.quilacarne.utils.ReservationTimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TablesViewModel(private val repository: SyncRepository) : ViewModel() {

    private val _isSyncComplete = MutableStateFlow(true)
    val isSyncComplete: StateFlow<Boolean> = _isSyncComplete
    private var liveSyncJob: Job? = null

    init {
        startLiveSync()
    }

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

    val reservationsByTable: StateFlow<Map<UUID, ReservationEntity>> = repository.getReservationsFlow()
        .map { reservations ->
            val nowMillis = System.currentTimeMillis()
            reservations
                .groupBy { it.tableId }
                .mapNotNull { (tableId, tableReservations) ->
                    ReservationTimeUtils.selectCurrentOrUpcoming(tableReservations, nowMillis)
                        ?.let { reservation -> tableId to reservation }
                }
                .toMap()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val waiterNamesByTable: StateFlow<Map<UUID, String>> = combine(
        tables,
        statuses,
        repository.getOrdersFlow(),
        repository.getUsersFlow()
    ) { tables, statuses, orders, users ->
        val statusesById = statuses.associateBy { it.id }

        tables
            .mapNotNull { table ->
                val tableStatusToken = table.statusId
                    ?.let { statusesById[it]?.token }
                    ?.uppercase()

                if (tableStatusToken != "OCCUPIED") {
                    return@mapNotNull null
                }

                val waiterName = orders
                    .firstOrNull { it.tableId == table.id }
                    ?.waiterId
                    ?.let { waiterId ->
                        users.firstOrNull { it.id == waiterId }?.username
                    }

                waiterName?.let { table.id to it }
            }
            .toMap()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun refreshTables() {
        viewModelScope.launch {
            try {
                val result = repository.syncOperationalData()

                result.onSuccess {
                    Log.d("TABLES_SYNC", "Synchronizacja stolików zakończona")
                }

                result.onFailure { error ->
                    Log.e("TABLES_SYNC", "Błąd sync: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("TABLES_SYNC", "Wyjątek: ${e.message}")
            } finally {
                _isSyncComplete.value = true
            }
        }
    }

    private fun startLiveSync() {
        if (liveSyncJob?.isActive == true) return

        liveSyncJob = viewModelScope.launch {
            while (true) {
                repository.syncOperationalData()
                    .onFailure { error ->
                        Log.w("TABLES_SYNC", "Okresowa synchronizacja nie powiodla sie: ${error.message}")
                    }
                delay(5_000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveSyncJob?.cancel()
    }
}
