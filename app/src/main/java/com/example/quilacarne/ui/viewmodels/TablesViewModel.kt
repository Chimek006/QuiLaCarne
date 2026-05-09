package com.example.quilacarne.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.repository.SyncRepository
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

    val waiterNamesByTable: StateFlow<Map<UUID, String>> = combine(
        tables,
        statuses,
        repository.getOrdersFlow(),
        repository.getUsersFlow()
    ) { tables, statuses, orders, users ->
        val occupiedStatusIds = statuses
            .filter { it.token.uppercase() == "OCCUPIED" }
            .map { it.id }
            .toSet()

        tables
            .filter { it.statusId in occupiedStatusIds }
            .mapNotNull { table ->
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
                val result = repository.syncTables()

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
}
