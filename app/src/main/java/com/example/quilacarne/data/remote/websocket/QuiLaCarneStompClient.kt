package com.example.quilacarne.data.remote.websocket

import android.util.Log
import com.example.quilacarne.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class QuiLaCarneStompClient(
    private val scope: CoroutineScope,
    private val listener: QuiLaCarneStompListener,
    private val endpointUrl: String = BuildConfig.WEBSOCKET_URL,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val subscriptionTopicsById = mutableMapOf<String, String>()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var endpoint: ResolvedWebSocketEndpoint? = null

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var manualDisconnect = false

    @Volatile
    private var connecting = false

    @Volatile
    private var connected = false

    private var heartbeatJob: Job? = null

    val isConnecting: Boolean
        get() = connecting

    val isConnected: Boolean
        get() = connected

    @Synchronized
    fun connect(token: String) {
        if (token.isBlank()) {
            listener.onError("Missing access token")
        } else if (!connecting && !connected) {
            openSocket(token)
        }
    }

    private fun openSocket(token: String) {
        val resolvedEndpoint = runCatching {
            WebSocketEndpointResolver.resolve(endpointUrl)
        }.onFailure { error ->
            listener.onError("Invalid WebSocket URL: ${error.message}", error)
        }.getOrNull()

        if (resolvedEndpoint != null) {
            manualDisconnect = false
            connecting = true
            connected = false
            accessToken = token
            endpoint = resolvedEndpoint
            subscriptionTopicsById.clear()

            val request = Request.Builder()
                .url(resolvedEndpoint.url)
                .build()

            Log.d(TAG, "Opening ${resolvedEndpoint.mode} STOMP connection to ${resolvedEndpoint.redactedUrl}")
            webSocket = okHttpClient.newWebSocket(request, SocketListener())
        }
    }

    @Synchronized
    fun disconnect(reason: String = "manual") {
        manualDisconnect = true
        connecting = false
        connected = false
        stopHeartbeat()
        subscriptionTopicsById.clear()
        webSocket?.close(NORMAL_CLOSURE_STATUS, reason)
        webSocket = null
        accessToken = null
    }

    private fun handleSocketOpened(socket: WebSocket) {
        if (endpoint?.usesSockJs == true) {
            Log.d(TAG, "SockJS transport opened, waiting for open frame")
        } else {
            sendConnectFrame(socket)
        }
    }

    private fun handleTransportMessage(socket: WebSocket, message: String) {
        val currentEndpoint = endpoint ?: return
        if (currentEndpoint.usesSockJs) {
            handleSockJsMessage(socket, message)
        } else {
            handleStompFrames(socket, message)
        }
    }

    private fun handleSockJsMessage(socket: WebSocket, message: String) {
        when {
            message == SOCKJS_OPEN -> sendConnectFrame(socket)
            message == SOCKJS_HEARTBEAT -> Unit
            message.startsWith(SOCKJS_MESSAGE_PREFIX) -> handleSockJsPayload(socket, message)
            message.startsWith(SOCKJS_CLOSE_PREFIX) -> closeFromServer(message)
            else -> Log.w(TAG, "Unknown SockJS frame: ${message.take(MAX_LOG_MESSAGE_LENGTH)}")
        }
    }

    private fun handleSockJsPayload(socket: WebSocket, message: String) {
        val frames = runCatching {
            JsonParser.parseString(message.substring(1)).asJsonArray.map { it.asString }
        }.getOrElse { error ->
            listener.onError("Invalid SockJS payload", error)
            return
        }

        frames.forEach { frame ->
            handleStompFrames(socket, frame)
        }
    }

    private fun closeFromServer(message: String) {
        connected = false
        connecting = false
        stopHeartbeat()
        val reason = "SockJS close: ${message.take(MAX_LOG_MESSAGE_LENGTH)}"
        Log.w(TAG, reason)
        if (!manualDisconnect) {
            listener.onDisconnected(reason)
        }
    }

    private fun handleStompFrames(socket: WebSocket, rawMessage: String) {
        rawMessage
            .split(STOMP_TERMINATOR)
            .asSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && it != STOMP_HEARTBEAT }
            .mapNotNull(::parseStompFrame)
            .forEach { frame -> handleStompFrame(socket, frame) }
    }

    private fun handleStompFrame(socket: WebSocket, frame: StompFrame) {
        when (frame.command) {
            "CONNECTED" -> {
                connecting = false
                connected = true
                Log.d(TAG, "STOMP CONNECTED")
                subscribeToTopics(socket)
                startHeartbeat()
                listener.onConnected()
            }
            "MESSAGE" -> {
                val topic = frame.headers["destination"]
                    ?: frame.headers["subscription"]?.let(subscriptionTopicsById::get)
                    ?: UNKNOWN_TOPIC
                listener.onMessage(topic, frame.body)
            }
            "ERROR" -> {
                val details = frame.headers["message"] ?: frame.body.take(MAX_LOG_MESSAGE_LENGTH)
                listener.onError("STOMP ERROR: $details")
                socket.close(PROTOCOL_ERROR_STATUS, details)
            }
            "RECEIPT" -> Unit
            else -> Log.w(TAG, "Unhandled STOMP command=${frame.command}")
        }
    }

    private fun sendConnectFrame(socket: WebSocket) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            listener.onError("Missing access token before CONNECT")
            socket.close(PROTOCOL_ERROR_STATUS, "missing-token")
            return
        }

        sendStompFrame(
            socket = socket,
            command = "CONNECT",
            headers = mapOf(
                "accept-version" to "1.2",
                "heart-beat" to "10000,10000",
                "Authorization" to "Bearer $token"
            )
        )
    }

    private fun subscribeToTopics(socket: WebSocket) {
        TOPICS.forEachIndexed { index, topic ->
            val subscriptionId = "qlc-$index"
            subscriptionTopicsById[subscriptionId] = topic
            sendStompFrame(
                socket = socket,
                command = "SUBSCRIBE",
                headers = mapOf(
                    "id" to subscriptionId,
                    "destination" to topic,
                    "ack" to "auto"
                )
            )
            Log.d(TAG, "Subscribed to $topic")
        }
    }

    private fun sendStompFrame(
        socket: WebSocket,
        command: String,
        headers: Map<String, String> = emptyMap(),
        body: String = ""
    ) {
        val frame = buildString {
            append(command).append('\n')
            headers.forEach { (key, value) ->
                append(key).append(':').append(value).append('\n')
            }
            append('\n')
            append(body)
            append(STOMP_TERMINATOR)
        }
        sendRawStomp(socket, frame)
    }

    private fun sendRawStomp(socket: WebSocket, frame: String) {
        val currentEndpoint = endpoint
        val payload = if (currentEndpoint?.usesSockJs == true) {
            gson.toJson(listOf(frame))
        } else {
            frame
        }
        socket.send(payload)
    }

    private fun parseStompFrame(rawFrame: String): StompFrame? {
        val normalized = rawFrame.replace("\r\n", "\n").trimStart('\n')
        var parsedFrame: StompFrame? = null

        if (normalized.isNotBlank()) {
            val headerEnd = normalized.indexOf("\n\n")
            val headerBlock = if (headerEnd >= 0) normalized.substring(0, headerEnd) else normalized
            val body = if (headerEnd >= 0) normalized.substring(headerEnd + 2) else ""
            val lines = headerBlock.lines().filter { it.isNotBlank() }
            val command = lines.firstOrNull()?.trim().orEmpty()

            if (command.isNotBlank()) {
                val headers = lines.drop(1).mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    line.substring(0, separator) to line.substring(separator + 1)
                }.toMap()

                parsedFrame = StompFrame(command = command, headers = headers, body = body)
            }
        }

        return parsedFrame
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive && connected) {
                delay(HEARTBEAT_INTERVAL_MS)
                webSocket?.let { socket ->
                    sendRawStomp(socket, STOMP_HEARTBEAT)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            handleSocketOpened(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleTransportMessage(webSocket, text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleTransportMessage(webSocket, bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connecting = false
            connected = false
            stopHeartbeat()
            if (!manualDisconnect) {
                listener.onDisconnected("closed code=$code reason=$reason")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connecting = false
            connected = false
            stopHeartbeat()
            val code = response?.code?.let { " http=$it" }.orEmpty()
            if (!manualDisconnect) {
                listener.onError("transport failure$code: ${t.message}", t)
            }
        }
    }

    private companion object {
        const val TAG = "QLC_WS"
        const val SOCKJS_OPEN = "o"
        const val SOCKJS_HEARTBEAT = "h"
        const val SOCKJS_MESSAGE_PREFIX = "a"
        const val SOCKJS_CLOSE_PREFIX = "c"
        const val STOMP_TERMINATOR = '\u0000'
        const val STOMP_HEARTBEAT = "\n"
        const val UNKNOWN_TOPIC = "unknown"
        const val NORMAL_CLOSURE_STATUS = 1000
        const val PROTOCOL_ERROR_STATUS = 1002
        const val HEARTBEAT_INTERVAL_MS = 10_000L
        const val MAX_LOG_MESSAGE_LENGTH = 160

        val TOPICS = listOf(
            "/topic/tables/updates",
            "/topic/reservations/updates",
            "/topic/orders/updates",
            "/topic/orders/items",
            "/topic/menu/dishes",
            "/topic/menu/availability",
            "/topic/dictionary/sync",
            "/topic/dictionary/allergens",
            "/topic/dictionary/dish-categories",
            "/topic/dictionary/table-statuses",
            "/topic/dictionary/order-statuses",
            "/topic/dictionary/order-item-statuses",
            "/topic/reports/updates",
            "/topic/personnel/updates",
            "/topic/security/bans"
        )
    }
}
