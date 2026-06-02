package com.example.quilacarne.data.remote.websocket

internal data class ResolvedWebSocketEndpoint(
    val url: String,
    val usesSockJs: Boolean,
    val mode: String
) {
    val redactedUrl: String
        get() = url.substringBeforeLast("/", missingDelimiterValue = url) + "/..."
}
