package com.example.quilacarne.data.repository.sync.logic

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import java.util.UUID

internal object SyncForeignKeyGuard {
    fun filterReservationsByKnownTables(
        reservations: List<ReservationEntity>,
        knownTableIds: Set<UUID>
    ): List<ReservationEntity> {
        return reservations.filter { reservation -> reservation.tableId in knownTableIds }
    }

    fun sanitizeOrders(
        orders: List<OrderEntity>,
        knownTableIds: Set<UUID>,
        knownUserIds: Set<UUID>
    ): List<OrderEntity> {
        return orders
            .filter { order -> order.tableId == null || order.tableId in knownTableIds }
            .map { order ->
                if (order.waiterId != null && order.waiterId !in knownUserIds) {
                    order.copy(waiterId = null)
                } else {
                    order
                }
            }
    }

    fun filterOrderItems(
        items: List<OrderItemEntity>,
        knownOrderIds: Set<UUID>,
        knownDishIds: Set<UUID>
    ): List<OrderItemEntity> {
        return items.filter { item ->
            item.orderId in knownOrderIds &&
                (item.productId == null || item.productId in knownDishIds)
        }
    }
}
