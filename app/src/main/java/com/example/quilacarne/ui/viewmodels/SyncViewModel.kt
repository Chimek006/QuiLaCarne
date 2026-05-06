package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.DatabaseProvider
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.repository.OrderRepository
import com.example.quilacarne.data.repository.SyncRepository
import com.example.quilacarne.data.remote.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val orderRepository = OrderRepository()
    private val db = DatabaseProvider.getDatabase(application)
    private val syncRepository = SyncRepository(db)

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState

    fun startSync() {
        viewModelScope.launch {
            _uiState.value = SyncUiState.Loading("Inicjalizacja manifestu...", 0.05f)

            orderRepository.getBootstrap().fold(
                onSuccess = { bootstrap: BootstrapResponse ->
                    try {
                        _uiState.value = SyncUiState.Loading("Pobieranie menu i kategorii...", 0.10f)
                        syncRepository.syncMenu().onFailure { error ->
                            throw Exception("Błąd synchronizacji menu: ${error.message}")
                        }

                        _uiState.value = SyncUiState.Loading("Aktualizacja statusów...", 0.15f)
                        orderRepository.getOrderItemStatuses().onSuccess { statuses: List<DictionaryItem> ->
                            val statusEntities = statuses.map { dto ->
                                TableStatusEntity(
                                    id = UUID.nameUUIDFromBytes(dto.token.toByteArray()),
                                    token = dto.token,
                                    namePl = dto.name,
                                    nameEn = dto.name,
                                    createdAt = "",
                                    updatedAt = ""
                                )
                            }
                            db.tableStatusDao().insertAll(statusEntities)
                        }

                        val totalOrderPages = bootstrap.modules["orders"]?.totalPages ?: 0
                        if (totalOrderPages > 0) {
                            for (page in 1..totalOrderPages) {
                                val progress = 0.2f + (page.toFloat() / totalOrderPages * 0.3f)
                                _uiState.value = SyncUiState.Loading("Pobieranie zamówień (strona $page/$totalOrderPages)", progress)

                                orderRepository.getOrdersSync(page).onSuccess { ordersDto: List<OrderSyncDto> ->
                                    val entities = ordersDto.mapNotNull { dto ->
                                        try {
                                            OrderEntity(
                                                id = UUID.fromString(dto.token),
                                                tableId = UUID.fromString(dto.tableToken),
                                                waiterId = null,
                                                statusId = dto.statusTokens.firstOrNull()?.let {
                                                    try { UUID.fromString(it) } catch(e: Exception) { UUID.nameUUIDFromBytes(it.toByteArray()) }
                                                } ?: UUID.randomUUID(),
                                                totalPrice = dto.totalPrice.toInt(),
                                                createdAt = dto.createdAt,
                                                updatedAt = dto.updatedAt
                                            )
                                        } catch (e: Exception) { null }
                                    }
                                    db.orderDao().insertOrders(entities)
                                }
                            }
                        }

                        val totalItemPages = bootstrap.modules["orderItems"]?.totalPages ?: 0
                        if (totalItemPages > 0) {
                            for (page in 1..totalItemPages) {
                                val progress = 0.6f + (page.toFloat() / totalItemPages * 0.35f)
                                _uiState.value = SyncUiState.Loading("Pobieranie pozycji zamówień (strona $page/$totalItemPages)", progress)

                                orderRepository.getOrderItemsSync(page).onSuccess { itemsDto: List<OrderItemSyncDto> ->
                                    val entities = itemsDto.mapNotNull { dto ->
                                        try {
                                            OrderItemEntity(
                                                id = UUID.fromString(dto.token),
                                                orderId = UUID.fromString(dto.orderToken),
                                                productId = UUID.fromString(dto.productToken),
                                                quantity = dto.quantity,
                                                priceAtTimeOfOrder = dto.priceAtTimeOfOrder.toInt(),
                                                createdAt = dto.createdAt,
                                                updatedAt = dto.updatedAt
                                            )
                                        } catch (e: Exception) { null }
                                    }
                                    db.orderDao().insertOrderItems(entities)
                                }
                            }
                        }

                        _uiState.value = SyncUiState.Success
                    } catch (e: Exception) {
                        _uiState.value = SyncUiState.Error("Błąd zapisu danych: ${e.message}")
                    }
                },
                onFailure = { error ->
                    _uiState.value = SyncUiState.Error("Błąd sieci (bootstrap): ${error.message}")
                }
            )
        }
    }
}

sealed class SyncUiState {
    object Idle : SyncUiState()
    data class Loading(val message: String, val progress: Float) : SyncUiState()
    object Success : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}