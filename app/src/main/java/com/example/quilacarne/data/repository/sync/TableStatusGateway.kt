package com.example.quilacarne.data.repository.sync

import java.util.Locale
import java.util.UUID

internal data class TableStatusChange(
    val tableId: UUID,
    val tableToken: String,
    val statusToken: String
)

internal interface TableStatusGateway {
    suspend fun changeStatus(change: TableStatusChange)
}

internal object TableStatusChangePlan {
    fun normalizeStatusToken(statusToken: String): String {
        val normalized = statusToken.trim().uppercase(Locale.US)
        if (normalized in SUPPORTED_STATUS_TOKENS) return normalized

        throw UnsupportedOperationException(
            "API nie udostepnia bezposredniej zmiany statusu stolika na $statusToken"
        )
    }

    fun endpointPath(change: TableStatusChange): String {
        return when (change.statusToken) {
            STATUS_AVAILABLE -> "tables/${change.tableToken}/avalaible"
            STATUS_CLEANING -> "tables/${change.tableToken}/clear"
            STATUS_OUT_OF_SERVICE -> "tables/${change.tableToken}/out-of-services"
            else -> unsupportedStatus(change.statusToken)
        }
    }

    fun optimisticAction(change: TableStatusChange): String {
        return when (change.statusToken) {
            STATUS_AVAILABLE -> PendingRequestRepository.ACTION_MARK_AVAILABLE
            STATUS_CLEANING -> PendingRequestRepository.ACTION_MARK_CLEANING
            STATUS_OUT_OF_SERVICE -> PendingRequestRepository.ACTION_MARK_OUT_OF_SERVICE
            else -> unsupportedStatus(change.statusToken)
        }
    }

    private fun unsupportedStatus(statusToken: String): Nothing {
        throw UnsupportedOperationException(
            "API nie udostepnia bezposredniej zmiany statusu stolika na $statusToken"
        )
    }

    private const val STATUS_AVAILABLE = "AVAILABLE"
    private const val STATUS_CLEANING = "CLEANING"
    private const val STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE"
    private val SUPPORTED_STATUS_TOKENS = setOf(
        STATUS_AVAILABLE,
        STATUS_CLEANING,
        STATUS_OUT_OF_SERVICE
    )
}
