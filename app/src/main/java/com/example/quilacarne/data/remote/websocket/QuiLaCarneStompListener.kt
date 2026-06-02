package com.example.quilacarne.data.remote.websocket

interface QuiLaCarneStompListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onError(reason: String, error: Throwable? = null)
    fun onMessage(topic: String, body: String)
}
