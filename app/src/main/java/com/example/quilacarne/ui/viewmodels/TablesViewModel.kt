package com.example.quilacarne.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TablesViewModel(private val repository: SyncRepository) : ViewModel() {

    private val _isSyncComplete = MutableStateFlow(false)
    val isSyncComplete: StateFlow<Boolean> = _isSyncComplete

    val tables: StateFlow<List<RestaurantTableEntity>> = repository.getTablesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshTables()
    }

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