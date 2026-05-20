package com.example.quilacarne.data.remote.network

import android.util.Log
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RealtimeSyncClient(
    private val tokenManager: TokenManager,
    private val syncRepository: SyncRepository,
    private val scope: CoroutineScope,
    private val waiterNotificationHelper: WaiterAssignmentNotificationHelper? = null,
    private val dishReadyNotificationHelper: DishReadyNotificationHelper? = null,
    private val webSocketUrl: String = BuildConfig.WEBSOCKET_URL
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    private val syncInProgress = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var activeToken: String? = null
    private var manuallyStopped = true
    private var disabledLogShown = false
    private var invalidUrlLogShown = false
    private var authRejectedLogShown = false
    private var reconnectAttempt = 0
    private var authRejectedToken: String? = null

    fun isConfigured(): Boolean {
        return webSocketUrl.isNotBlank() && isUrlAllowedForBuild()
    }

    fun isConnected(): Boolean {
        return connected.get()
    }

    fun start() {
        if (webSocketUrl.isBlank()) {
            logDisabledOnce()
        } else if (!isUrlAllowedForBuild()) {
            logInvalidUrlOnce()
        } else {
            val token = tokenManager.getAccessToken()

            when {
                token.isNullOrBlank() -> stop()
                authRejectedToken == token -> logAuthRejectedOnce()
                webSocket != null && activeToken == token -> Unit
                reconnectJob?.isActive == true && activeToken == token -> Unit
                else -> {
                    authRejectedToken = null
                    authRejectedLogShown = false
                    connect(token)
                }
            }
        }
    }

    fun stop() {
        manuallyStopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        activeToken = null
        reconnectAttempt = 0
        connected.set(false)
        webSocket?.close(NORMAL_CLOSE_CODE, "Realtime sync stopped")
        webSocket = null
    }

    fun shutdown() {
        stop()
        client.dispatcher.executorService.shutdown()
    }

    private fun connect(token: String) {
        webSocket?.close(NORMAL_CLOSE_CODE, "Realtime sync reconnecting")
        reconnectJob?.cancel()
        reconnectJob = null
        manuallyStopped = false
        activeToken = token

        val request = Request.Builder()
            .url(webSocketUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        Log.d(TAG, "Connecting WebSocket target=${request.url.scheme}://${request.url.host}")
        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                reconnectAttempt = 0
                Log.d(TAG, "WebSocket connected code=${response.code}")
                triggerOperationalSync("connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = RealtimeEventParser.parse(text)
                if (event == null) {
                    Log.d(TAG, "WebSocket untyped message received")
                    triggerOperationalSync("untyped-message")
                } else {
                    Log.d(TAG, "WebSocket event received type=${event.type}")
                    handleEvent(event)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "WebSocket binary message received size=${bytes.size}")
                triggerOperationalSync("binary-message")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing code=$code reason=$reason")
                webSocket.close(NORMAL_CLOSE_CODE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed code=$code reason=$reason")
                connected.set(false)
                this@RealtimeSyncClient.webSocket = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure code=${response?.code} message=${t.message}")
                connected.set(false)
                this@RealtimeSyncClient.webSocket = null
                if (response?.code in AUTH_REJECTED_CODES) {
                    authRejectedToken = activeToken
                    reconnectJob?.cancel()
                    reconnectJob = null
                    manuallyStopped = true
                    Log.w(TAG, "WebSocket authorization rejected; waiting for refreshed access token")
                } else {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!manuallyStopped && reconnectJob?.isActive != true) {
            val delayMs = nextReconnectDelayMs()
            reconnectJob = scope.launch {
                delay(delayMs)
                start()
            }
        }
    }

    private fun nextReconnectDelayMs(): Long {
        val multiplier = 1L shl reconnectAttempt.coerceAtMost(MAX_RECONNECT_SHIFT)
        reconnectAttempt += 1
        return (INITIAL_RECONNECT_DELAY_MS * multiplier).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private fun handleEvent(event: RealtimeEvent) {
        when (event.type) {
            EVENT_MENU_CHANGED -> triggerMenuSync(event.type)
            EVENT_TABLE_STATUS_CHANGED,
            EVENT_ORDER_STATUS_CHANGED -> triggerOperationalSync("event:${event.type}")
            EVENT_ORDER_ITEM_STATUS_CHANGED,
            EVENT_DISH_READY -> {
                triggerOperationalSync("event:${event.type}")
                notifyDishReady(event)
            }
            EVENT_WAITER_ASSIGNED -> {
                triggerOperationalSync("event:${event.type}")
                notifyWaiterAssigned(event)
            }
            else -> {
                // Backend contract note: align event names once the WebSocket contract is documented.
                triggerOperationalSync("event:${event.type}")
            }
        }
    }

    private fun notifyDishReady(event: RealtimeEvent) {
        dishReadyNotificationHelper?.notifyIfDishReady(event)
    }

    private fun triggerMenuSync(reason: String) {
        scope.launch {
            Log.d(TAG, "Realtime menu sync trigger reason=$reason")
            syncRepository.syncMenu()
                .onSuccess {
                    Log.d(TAG, "Realtime menu sync finished reason=$reason")
                }
                .onFailure { error ->
                    Log.e(TAG, "Realtime menu sync failed reason=$reason message=${error.message}")
                }
        }
    }

    private fun notifyWaiterAssigned(event: RealtimeEvent) {
        val helper = waiterNotificationHelper ?: return
        scope.launch {
            helper.notifyIfAssignedToCurrentUser(event)
        }
    }

    private fun triggerOperationalSync(reason: String) {
        if (syncInProgress.compareAndSet(false, true)) {
            scope.launch {
                Log.d(TAG, "Realtime sync trigger reason=$reason")
                syncRepository.syncOperationalData("websocket-$reason")
                    .onSuccess {
                        Log.d(TAG, "Realtime operational sync finished reason=$reason")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Realtime operational sync failed reason=$reason message=${error.message}")
                    }

                syncInProgress.set(false)
            }
        } else {
            Log.d(TAG, "Realtime operational sync skipped reason=$reason")
        }
    }

    private fun isUrlAllowedForBuild(): Boolean {
        return BuildConfig.DEBUG || webSocketUrl.startsWith("wss://", ignoreCase = true)
    }

    private fun logDisabledOnce() {
        if (!disabledLogShown) {
            disabledLogShown = true
            Log.d(TAG, "WebSocket disabled because WEBSOCKET_URL is empty. Set WEBSOCKET_URL=wss://... in local.properties")
        }
    }

    private fun logInvalidUrlOnce() {
        if (!invalidUrlLogShown) {
            invalidUrlLogShown = true
            Log.w(TAG, "WebSocket disabled because release builds require wss://")
        }
    }

    private fun logAuthRejectedOnce() {
        if (!authRejectedLogShown) {
            authRejectedLogShown = true
            Log.w(TAG, "WebSocket not reconnecting with rejected access token")
        }
    }

    private companion object {
        const val TAG = "REALTIME_WS"
        const val NORMAL_CLOSE_CODE = 1000
        const val PING_INTERVAL_SECONDS = 30L
        const val INITIAL_RECONNECT_DELAY_MS = 5_000L
        const val MAX_RECONNECT_DELAY_MS = 60_000L
        const val MAX_RECONNECT_SHIFT = 4
        const val EVENT_TABLE_STATUS_CHANGED = "TABLE_STATUS_CHANGED"
        const val EVENT_MENU_CHANGED = "MENU_CHANGED"
        const val EVENT_WAITER_ASSIGNED = "WAITER_ASSIGNED"
        const val EVENT_ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED"
        const val EVENT_ORDER_ITEM_STATUS_CHANGED = "ORDER_ITEM_STATUS_CHANGED"
        const val EVENT_DISH_READY = "DISH_READY"
        val AUTH_REJECTED_CODES = setOf(401, 403)
    }
}
