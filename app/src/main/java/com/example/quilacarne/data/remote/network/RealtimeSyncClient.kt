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
    private val webSocketUrl: String = BuildConfig.WEBSOCKET_URL
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    private val syncInProgress = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var activeToken: String? = null
    private var manuallyStopped = true
    private var disabledLogShown = false

    fun start() {
        if (webSocketUrl.isBlank()) {
            logDisabledOnce()
        } else {
            val token = tokenManager.getAccessToken()

            when {
                token.isNullOrBlank() -> stop()
                webSocket != null && activeToken == token -> Unit
                reconnectJob?.isActive == true && activeToken == token -> Unit
                else -> connect(token)
            }
        }
    }

    fun stop() {
        manuallyStopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        activeToken = null
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

        Log.d(TAG, "Connecting WebSocket url=$webSocketUrl")
        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected code=${response.code}")
                triggerOperationalSync("connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received preview=${text.take(LOG_PREVIEW_LIMIT)}")
                triggerOperationalSync("text-message")
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
                this@RealtimeSyncClient.webSocket = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure code=${response?.code} message=${t.message}")
                this@RealtimeSyncClient.webSocket = null
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (!manuallyStopped && reconnectJob?.isActive != true) {
            reconnectJob = scope.launch {
                delay(RECONNECT_DELAY_MS)
                start()
            }
        }
    }

    private fun triggerOperationalSync(reason: String) {
        if (syncInProgress.compareAndSet(false, true)) {
            scope.launch {
                syncRepository.syncOperationalData()
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

    private fun logDisabledOnce() {
        if (!disabledLogShown) {
            disabledLogShown = true
            Log.d(TAG, "WebSocket disabled because WEBSOCKET_URL is empty")
        }
    }

    private companion object {
        const val TAG = "REALTIME_WS"
        const val NORMAL_CLOSE_CODE = 1000
        const val PING_INTERVAL_SECONDS = 30L
        const val RECONNECT_DELAY_MS = 5_000L
        const val LOG_PREVIEW_LIMIT = 120
    }
}
