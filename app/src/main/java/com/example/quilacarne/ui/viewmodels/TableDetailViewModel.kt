package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.DatabaseProvider
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.relations.OrderItemWithDish
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class TableDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DatabaseProvider.getDatabase(application)

    private val _orderItems = MutableStateFlow<List<OrderItemWithDish>>(emptyList())
    val orderItems: StateFlow<List<OrderItemWithDish>> = _orderItems

    private val _statusDictionary = MutableStateFlow<List<TableStatusEntity>>(emptyList())
    val statusDictionary: StateFlow<List<TableStatusEntity>> = _statusDictionary

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var itemsJob: Job? = null

    fun loadTableData(tableId: UUID) {
        viewModelScope.launch {
            db.tableStatusDao().getAllStatuses().collect { statuses ->
                _statusDictionary.value = statuses
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                db.orderDao().getAllOrders().collectLatest { orders ->
                    val activeOrder = orders.find { it.tableId == tableId }

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
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        itemsJob?.cancel()
    }
}