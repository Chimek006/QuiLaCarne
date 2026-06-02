package com.example.quilacarne.data.repository.sync.logic

import com.example.quilacarne.data.repository.sync.model.TableStatusSyncDecision
import java.util.Locale

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
