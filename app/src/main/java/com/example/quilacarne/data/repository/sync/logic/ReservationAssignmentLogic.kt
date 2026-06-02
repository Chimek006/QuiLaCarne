package com.example.quilacarne.data.repository.sync.logic

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import java.util.Locale

internal object ReservationAssignmentLogic {
    private val alreadyAssignedErrorMarkers = listOf(
        "already been assigned",
        "reservation is in progress",
        "order not found",
        "reservation not found"
    )

    fun shouldTreatAssignFailureAsSuccess(
        errorMessage: String?,
        order: OrderEntity?,
        reservation: ReservationEntity?
    ): Boolean {
        return isAlreadyAssignedError(errorMessage) &&
            isAssignmentApplied(order, reservation)
    }

    fun isAlreadyAssignedError(errorMessage: String?): Boolean {
        val normalizedMessage = errorMessage
            ?.lowercase(Locale.US)
            ?: return false

        return alreadyAssignedErrorMarkers.any { marker ->
            normalizedMessage.contains(marker)
        }
    }

    fun isAssignmentApplied(
        order: OrderEntity?,
        reservation: ReservationEntity?
    ): Boolean {
        return order?.waiterId != null ||
            hasStatus(order?.statusTokens, "IN_PROGRESS") ||
            hasStatus(reservation?.statusTokens, "IN_PROGRESS")
    }

    private fun hasStatus(statusTokens: String?, token: String): Boolean {
        return statusTokens
            .orEmpty()
            .split(',')
            .map { it.trim().uppercase(Locale.US) }
            .any { it == token }
    }
}
