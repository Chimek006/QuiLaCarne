package com.example.quilacarne.ui.viewmodels

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import java.util.UUID

internal object TableDetailOrderSelectionLogic {
    fun findOrderForReservation(
        tableOrders: List<OrderEntity>,
        reservation: ReservationEntity?,
        reservationTokenForOrder: (UUID) -> String?
    ): OrderEntity? {
        val reservationToken = reservation
            ?.token
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return tableOrders.firstOrNull { order ->
            reservationTokenForOrder(order.id)?.trim() == reservationToken
        }
    }
}
