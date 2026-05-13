package com.example.quilacarne.data.repository

import com.example.quilacarne.data.remote.dto.ApiResponse
import com.example.quilacarne.utils.ReservationTimeUtils
import com.google.gson.JsonParser
import java.util.Locale

internal object TableStatusSyncLogic {
    private val priority = listOf(
        "OUT_OF_SERVICE",
        "CLEANING",
        "OCCUPIED",
        "RESERVED",
        "AVAILABLE"
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
        val remoteToken = remoteStatusToken?.uppercase(Locale.US)
        val existingToken = existingStatusToken?.uppercase(Locale.US)

        if (remoteToken == null) return existingToken

        val remoteSaysAvailable = remoteToken == "AVAILABLE"
        val existingIsBusyState = existingToken in setOf("OCCUPIED", "CLEANING", "OUT_OF_SERVICE")

        if (remoteSaysAvailable && existingIsBusyState) {
            val remoteMillis = remoteUpdatedAt?.let { ReservationTimeUtils.parseApiTimestampMillis(it) }
            val existingMillis = existingUpdatedAt?.let { ReservationTimeUtils.parseApiTimestampMillis(it) }

            if (remoteMillis == null || existingMillis == null || remoteMillis <= existingMillis) {
                return existingToken
            }
        }

        return remoteToken
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
