package com.example.quilacarne.data.remote.websocket

internal data class StompFrame(
    val command: String,
    val headers: Map<String, String>,
    val body: String
)
