package com.example.quilacarne.utils

import com.example.quilacarne.data.local.entities.ReservationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReservationTimeUtils {
    private val inactiveStatuses = setOf(
        "ABSENT",
        "CANCELED",
        "CANCELLED",
        "COMPLETED",
        "DONE",
        "FINISHED",
        "NO_SHOW"
    )

    fun parseApiTimestampMillis(value: String): Long? {
        return parseApiTimestamp(value)?.time
    }

    fun isActiveStatus(statusTokens: List<String>): Boolean {
        return statusTokens.none { it.uppercase(Locale.US) in inactiveStatuses }
    }

    fun isCurrent(reservation: ReservationEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return reservation.isActive &&
            reservation.startEpochMillis <= nowMillis &&
            reservation.endEpochMillis > nowMillis
    }

    fun isUpcoming(reservation: ReservationEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return reservation.isActive && reservation.startEpochMillis > nowMillis
    }

    fun selectCurrentOrUpcoming(
        reservations: List<ReservationEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): ReservationEntity? {
        return reservations
            .filter { isCurrent(it, nowMillis) }
            .minByOrNull { it.startEpochMillis }
            ?: reservations
                .filter { isUpcoming(it, nowMillis) }
                .minByOrNull { it.startEpochMillis }
    }

    fun formatTimeRange(startTime: String, endTime: String): String {
        val start = parseApiTimestamp(startTime)
        val end = parseApiTimestamp(endTime)

        return if (start != null && end != null) {
            "${displayTimeFormat().format(start)} - ${displayTimeFormat().format(end)}"
        } else {
            "$startTime - $endTime"
        }
    }

    fun statusTokensToText(statusTokens: List<String>): String {
        return statusTokens.joinToString(",")
    }

    private fun parseApiTimestamp(value: String): Date? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        patterns.forEach { pattern ->
            runCatching { apiTimestampFormat(pattern).parse(value) }
                .getOrNull()
                ?.let { return it }
        }

        return null
    }

    private fun apiTimestampFormat(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun displayTimeFormat(): SimpleDateFormat {
        return SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
