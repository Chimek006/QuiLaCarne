package com.example.quilacarne.data.repository.sync

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

    @Suppress("UNUSED_PARAMETER")
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

        return if (remoteToken == null) {
            TableStatusSyncDecision(
                statusToken = existingToken,
                reason = "remote-empty-keep-local"
            )
        } else {
            TableStatusSyncDecision(
                statusToken = remoteToken,
                reason = "remote-status-accepted"
            )
        }
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
        "CLEANING"
    )

    @Suppress("UNUSED_PARAMETER")
    fun resolveDisplayStatusToken(
        physicalStatusToken: String?,
        hasActiveOrder: Boolean,
        hasReservation: Boolean,
        previousStatusToken: String?,
        isSyncing: Boolean
    ): String {
        val physicalToken = normalizeStatusToken(physicalStatusToken)
        return when {
            physicalToken != null && physicalToken in physicalStatusPriority -> physicalToken
            physicalToken == "AVAILABLE" -> if (hasReservation) "RESERVED" else "AVAILABLE"
            hasActiveOrder -> "OCCUPIED"
            hasReservation -> "RESERVED"
            physicalToken == "OCCUPIED" -> "AVAILABLE"
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
