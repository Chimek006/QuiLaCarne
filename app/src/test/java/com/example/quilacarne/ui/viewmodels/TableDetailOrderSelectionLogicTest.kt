package com.example.quilacarne.ui.viewmodels

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class TableDetailOrderSelectionLogicTest {
    @Test
    fun findOrderForReservationUsesOnlyMatchingReservationToken() {
        val tableId = UUID.randomUUID()
        val wrongOrder = order(tableId)
        val matchingOrder = order(tableId)
        val reservation = reservation(tableId, token = "reservation-current")
        val tokens = mapOf(
            wrongOrder.id to "reservation-previous",
            matchingOrder.id to "reservation-current"
        )

        val selected = TableDetailOrderSelectionLogic.findOrderForReservation(
            tableOrders = listOf(wrongOrder, matchingOrder),
            reservation = reservation,
            reservationTokenForOrder = tokens::get
        )

        assertEquals(matchingOrder.id, selected?.id)
    }

    @Test
    fun findOrderForReservationReturnsNullWhenNoOrderMatchesReservationToken() {
        val tableId = UUID.randomUUID()
        val firstOrder = order(tableId)
        val secondOrder = order(tableId)
        val reservation = reservation(tableId, token = "reservation-current")
        val tokens = mapOf(
            firstOrder.id to "reservation-previous",
            secondOrder.id to "reservation-next"
        )

        val selected = TableDetailOrderSelectionLogic.findOrderForReservation(
            tableOrders = listOf(firstOrder, secondOrder),
            reservation = reservation,
            reservationTokenForOrder = tokens::get
        )

        assertNull(selected)
    }

    private fun order(tableId: UUID): OrderEntity {
        return OrderEntity(
            id = UUID.randomUUID(),
            tableId = tableId,
            waiterId = null,
            statusId = null,
            statusTokens = "PENDING",
            createdAt = "created",
            updatedAt = "updated"
        )
    }

    private fun reservation(
        tableId: UUID,
        token: String
    ): ReservationEntity {
        return ReservationEntity(
            id = UUID.randomUUID(),
            token = token,
            tableId = tableId,
            tableToken = "table-token",
            userToken = "user-token",
            startTime = "2026-05-27T18:00:00Z",
            endTime = "2026-05-27T20:00:00Z",
            startEpochMillis = 1_000L,
            endEpochMillis = 2_000L,
            statusTokens = "PENDING",
            isActive = true,
            createdAt = "created",
            updatedAt = "updated"
        )
    }
}
