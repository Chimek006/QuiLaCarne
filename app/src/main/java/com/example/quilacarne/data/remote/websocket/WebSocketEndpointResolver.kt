package com.example.quilacarne.data.remote.websocket

import java.util.UUID
import kotlin.random.Random

internal object WebSocketEndpointResolver {
    fun resolve(configuredUrl: String): ResolvedWebSocketEndpoint {
        val trimmedUrl = configuredUrl.trim().trimEnd('/')
        require(trimmedUrl.isNotBlank()) { "WEBSOCKET_URL is blank" }

        return when {
            trimmedUrl.startsWith("https://", ignoreCase = true) ||
                trimmedUrl.startsWith("http://", ignoreCase = true) -> {
                val websocketBase = trimmedUrl
                    .replaceFirst("https://", "wss://", ignoreCase = true)
                    .replaceFirst("http://", "ws://", ignoreCase = true)
                val serverId = Random.nextInt(100, 1_000)
                val sessionId = UUID.randomUUID().toString().replace("-", "")
                val url = "$websocketBase/$serverId/$sessionId/websocket"
                ResolvedWebSocketEndpoint(url = url, usesSockJs = true, mode = "SockJS")
            }
            trimmedUrl.startsWith("wss://", ignoreCase = true) ||
                trimmedUrl.startsWith("ws://", ignoreCase = true) -> {
                ResolvedWebSocketEndpoint(url = trimmedUrl, usesSockJs = false, mode = "raw WebSocket")
            }
            else -> error("Unsupported WebSocket URL scheme")
        }
    }
}
