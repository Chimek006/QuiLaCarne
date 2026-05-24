package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import java.util.UUID

internal data class OrdersAndItemsSyncSnapshot(
    val orders: List<OrderEntity> = emptyList(),
    val orderTokens: List<OrderTokenSnapshot> = emptyList(),
    val orderItems: List<OrderItemEntity> = emptyList(),
    val orderItemTokens: List<Pair<UUID, String>> = emptyList()
)
