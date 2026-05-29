package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.google.gson.JsonParser
import java.util.Locale

internal data class OccupyReservationSelection(
    val token: String,
    val type: String
)

internal object ReservationSelectionLogic {
    fun chooseReservationForOccupy(
        currentToken: String?,
        upcomingToken: String?
    ): OccupyReservationSelection? {
        return when {
            !currentToken.isNullOrBlank() -> OccupyReservationSelection(
                token = currentToken,
                type = "current"
            )
            !upcomingToken.isNullOrBlank() -> OccupyReservationSelection(
                token = upcomingToken,
                type = "upcoming"
            )
            else -> null
        }
    }
}

internal object TableStatusSyncLogic {
    private val priority = listOf(
        "OUT_OF_SERVICE",
        "CLEANING",
        "AVAILABLE",
        "OCCUPIED",
        "RESERVED"
    )

    fun chooseStatusToken(tokens: List<String>): String? {
        val normalizedTokens = tokens
            .filter { it.isNotBlank() }
            .associateBy { it.uppercase(Locale.US) }

        if (normalizedTokens.isEmpty()) return null

        priority.forEach { preferredToken ->
            normalizedTokens[preferredToken]?.let { return it }
        }

        return tokens.firstOrNull { it.isNotBlank() }
    }

    fun resolveSyncedTableStatusToken(
        remoteStatusToken: String?,
        existingStatusToken: String?,
        remoteUpdatedAt: String?,
        existingUpdatedAt: String?
    ): String? {
        return resolveSyncedTableStatusDecision(
            remoteStatusToken = remoteStatusToken,
            existingStatusToken = existingStatusToken,
            remoteUpdatedAt = remoteUpdatedAt,
            existingUpdatedAt = existingUpdatedAt
        ).statusToken
    }

    fun resolveSyncedTableStatusDecision(
        remoteStatusToken: String?,
        existingStatusToken: String?,
        remoteUpdatedAt: String?,
        existingUpdatedAt: String?
    ): TableStatusSyncDecision {
        val remoteToken = remoteStatusToken
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)
        val existingToken = existingStatusToken
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)

        if (remoteToken == null) {
            return TableStatusSyncDecision(
                statusToken = existingToken,
                reason = "remote-empty-keep-local"
            )
        }

        return TableStatusSyncDecision(
            statusToken = remoteToken,
            reason = "remote-status-accepted"
        )
    }

    fun tableStatusNamePl(token: String): String {
        return when (token.uppercase(Locale.US)) {
            "AVAILABLE" -> "Wolny"
            "OCCUPIED" -> "Zajety"
            "RESERVED" -> "Zarezerwowany"
            "CLEANING" -> "Do sprzatniecia"
            "OUT_OF_SERVICE" -> "Wylaczony"
            else -> token.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    fun tableStatusNameEn(token: String): String {
        return when (token.uppercase(Locale.US)) {
            "AVAILABLE" -> "Available"
            "OCCUPIED" -> "Occupied"
            "RESERVED" -> "Reserved"
            "CLEANING" -> "Cleaning"
            "OUT_OF_SERVICE" -> "Out of service"
            else -> token.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

}

internal data class TableStatusSyncDecision(
    val statusToken: String?,
    val reason: String
)

internal object TableDisplayStatusLogic {
    private val physicalStatusPriority = setOf(
        "OUT_OF_SERVICE",
        "CLEANING",
        "OCCUPIED"
    )

    @Suppress("UNUSED_PARAMETER")
    fun resolveDisplayStatusToken(
        physicalStatusToken: String?,
        hasActiveOrder: Boolean,
        hasReservation: Boolean,
        hasInProgressReservation: Boolean = false,
        previousStatusToken: String?,
        isSyncing: Boolean
    ): String {
        val physicalToken = normalizeStatusToken(physicalStatusToken)
        return when {
            physicalToken != null && physicalToken in physicalStatusPriority -> physicalToken
            physicalToken == "AVAILABLE" -> if (hasReservation) "RESERVED" else "AVAILABLE"
            physicalToken == "RESERVED" -> "RESERVED"
            hasActiveOrder -> "OCCUPIED"
            hasInProgressReservation -> "OCCUPIED"
            hasReservation -> "RESERVED"
            physicalToken != null -> physicalToken
            else -> "AVAILABLE"
        }
    }

    private fun normalizeStatusToken(token: String?): String? {
        return token
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)
    }
}

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

internal object PendingTableStatusLogic {
    fun statusTokenForAction(action: String?): String? {
        return when (action) {
            PendingRequestRepository.ACTION_MARK_AVAILABLE -> "AVAILABLE"
            PendingRequestRepository.ACTION_MARK_CLEANING -> "CLEANING"
            PendingRequestRepository.ACTION_MARK_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
            else -> null
        }
    }
}

internal object ApiResponseLogic {
    fun errorMessageFromBody(rawErrorBody: String?): String? {
        val raw = rawErrorBody?.takeIf { it.isNotBlank() } ?: return null

        return runCatching {
            JsonParser.parseString(raw)
                .asJsonObject
                .get("message")
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: raw
    }

    fun <T> assertWrapperSuccess(
        body: ApiResponse<T>?,
        fallbackMessage: String
    ) {
        if (body == null) return

        val hasErrorMessages = body.errorMessages?.isNotEmpty() == true
        val statusCodeLooksSuccessful = body.statusCode in 200..299
        val wrapperLooksSuccessful = body.isSuccess ||
            statusCodeLooksSuccessful ||
            (!hasErrorMessages && body.statusCode == 0)

        if (!wrapperLooksSuccessful) {
            throw Exception(body.message ?: body.errorMessages?.joinToString() ?: fallbackMessage)
        }
    }
}
