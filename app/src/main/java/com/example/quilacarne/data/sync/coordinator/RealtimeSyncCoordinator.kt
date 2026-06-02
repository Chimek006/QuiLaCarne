package com.example.quilacarne.data.sync.coordinator

import android.util.Log
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.websocket.QuiLaCarneStompClient
import com.example.quilacarne.data.remote.websocket.QuiLaCarneStompListener
import com.example.quilacarne.data.repository.sync.SessionInvalidationReason
import com.example.quilacarne.data.repository.sync.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RealtimeSyncCoordinator(
    private val scope: CoroutineScope,
    private val tokenManager: TokenManager,
    private val syncRepository: SyncRepository,
    private val state: RealtimeSyncState,
    private val onSessionInvalidated: (SessionInvalidationReason) -> Unit = {},
    private val clientFactory: (QuiLaCarneStompListener) -> QuiLaCarneStompClient = { listener ->
        QuiLaCarneStompClient(scope = scope, listener = listener)
    },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val delayMillis: suspend (Long) -> Unit = { delay(it) }
) {
    private val client = clientFactory(RealtimeSyncClientListener(this))
    private var coordinatorJob: Job? = null
    private var lastConnectedToken: String? = null
    private var reconnectAttempt = 0
    private var nextConnectAt = 0L

    @Volatile
    private var active = false

    fun start(): Job {
        coordinatorJob?.takeIf { it.isActive }?.let { return it }
        active = true
        coordinatorJob = scope.launch {
            while (isActive) {
                tick()
                delayMillis(STATE_CHECK_INTERVAL_MS)
            }
        }
        return coordinatorJob!!
    }

    fun pause() {
        active = false
        coordinatorJob?.cancel()
        coordinatorJob = null
        client.disconnect("lifecycle-paused")
    }

    fun stop() {
        pause()
    }

    internal suspend fun tick() {
        val token = tokenManager.getAccessToken()
        val eligible = state.isOnline() &&
            state.isServerAvailable() &&
            state.isBootstrapped() &&
            !token.isNullOrBlank()

        if (!eligible) {
            if (client.isConnected || client.isConnecting) {
                Log.d(TAG, "Disconnecting WebSocket because realtime sync is not eligible")
                client.disconnect("not-eligible")
            }
            lastConnectedToken = null
            reconnectAttempt = 0
            nextConnectAt = 0L
            return
        }

        if (token != lastConnectedToken && (client.isConnected || client.isConnecting)) {
            Log.d(TAG, "Access token changed, reconnecting WebSocket")
            client.disconnect("token-changed")
            reconnectAttempt = 0
            nextConnectAt = 0L
        }

        if (!client.isConnected && !client.isConnecting && nowMillis() >= nextConnectAt) {
            lastConnectedToken = token
            Log.d(TAG, "Connecting WebSocket")
            client.connect(token)
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (!active) return

        val delay = RECONNECT_BACKOFF_MS[reconnectAttempt.coerceAtMost(RECONNECT_BACKOFF_MS.lastIndex)]
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(RECONNECT_BACKOFF_MS.lastIndex)
        nextConnectAt = nowMillis() + delay
        Log.d(TAG_RECONNECT, "Reconnect scheduled in ${delay}ms reason=$reason")
    }

    internal fun handleClientConnected() {
        reconnectAttempt = 0
        nextConnectAt = 0L
        scope.launch {
            runCatching {
                syncRepository.syncOperationalData("websocket-connected").getOrThrow()
                syncRepository.syncMenu().getOrThrow()
            }.onFailure { error ->
                Log.w(TAG, "REST fallback after WebSocket connect failed: ${error.message}")
            }
        }
    }

    internal fun handleClientDisconnected(reason: String) {
        Log.w(TAG, "Realtime STOMP disconnected: $reason")
        scheduleReconnect(reason)
    }

    internal fun handleClientError(reason: String, error: Throwable?) {
        Log.w(TAG, reason, error)
        scheduleReconnect(reason)
    }

    internal fun handleClientMessage(topic: String, body: String) {
        scope.launch {
            val sessionInvalidation = syncRepository.clearCurrentSessionForAccountEventIfNeeded(topic, body)
            if (sessionInvalidation != null) {
                Log.i(TAG, "Disconnecting WebSocket after account event cleared the current session")
                onSessionInvalidated(sessionInvalidation)
                client.disconnect("current-user-personnel-update")
                lastConnectedToken = null
                reconnectAttempt = 0
                nextConnectAt = 0L
            }

            syncRepository.applyWebSocketEvent(topic, body)
                .onFailure { error ->
                    Log.w(TAG_EVENT, "Failed to apply event topic=$topic message=${error.message}")
                }
        }
    }

    private companion object {
        const val TAG = "QLC_WS"
        const val TAG_EVENT = "QLC_WS_EVENT"
        const val TAG_RECONNECT = "QLC_WS_RECONNECT"
        const val STATE_CHECK_INTERVAL_MS = 2_000L
        val RECONNECT_BACKOFF_MS = listOf(2_000L, 5_000L, 10_000L, 30_000L)
    }
}
