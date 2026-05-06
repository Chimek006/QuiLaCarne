package com.example.quilacarne.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.repository.SyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TablesViewModel(private val repository: SyncRepository) : ViewModel() {

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
            repository.syncTables()
        }
    }
}