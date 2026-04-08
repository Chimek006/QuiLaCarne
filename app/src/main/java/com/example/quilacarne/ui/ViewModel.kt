package com.example.quilacarne.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.DatabaseProvider
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TableDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DatabaseProvider.getDatabase(application)

    private val _table = MutableStateFlow<RestaurantTableEntity?>(null)
    val table: StateFlow<RestaurantTableEntity?> = _table

    private val _orderItems = MutableStateFlow<List<OrderItemEntity>>(emptyList())
    val orderItems: StateFlow<List<OrderItemEntity>> = _orderItems

    fun loadTableData(tableId: UUID) {
        viewModelScope.launch {
        }
    }
}