package com.example.quilacarne.data.sync.coordinator

import android.util.Log
import com.example.quilacarne.data.remote.websocket.QuiLaCarneStompListener

internal class RealtimeSyncClientListener(
    private val coordinator: RealtimeSyncCoordinator
) : QuiLaCarneStompListener {
    override fun onConnected() {
        Log.d("QLC_WS", "Realtime STOMP connection ready")
        coordinator.handleClientConnected()
    }

    override fun onDisconnected(reason: String) {
        coordinator.handleClientDisconnected(reason)
    }

    override fun onError(reason: String, error: Throwable?) {
        coordinator.handleClientError(reason, error)
    }

    override fun onMessage(topic: String, body: String) {
        coordinator.handleClientMessage(topic, body)
    }
}
