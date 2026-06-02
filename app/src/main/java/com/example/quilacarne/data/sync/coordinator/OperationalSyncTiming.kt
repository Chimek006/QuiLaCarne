package com.example.quilacarne.data.sync.coordinator

import kotlinx.coroutines.delay

data class OperationalSyncTiming(
    val nowMillis: () -> Long = { System.currentTimeMillis() },
    val delayMillis: suspend (Long) -> Unit = { delay(it) },
    val connectionCheckIntervalMs: Long = DEFAULT_CONNECTION_CHECK_INTERVAL_MS,
    val pollingIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    companion object {
        const val DEFAULT_CONNECTION_CHECK_INTERVAL_MS = 15_000L
        const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}
