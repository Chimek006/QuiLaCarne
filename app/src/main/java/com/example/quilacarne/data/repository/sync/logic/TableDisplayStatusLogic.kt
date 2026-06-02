package com.example.quilacarne.data.repository.sync.logic

import java.util.Locale

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
