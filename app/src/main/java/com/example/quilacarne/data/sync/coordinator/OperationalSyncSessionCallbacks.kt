package com.example.quilacarne.data.sync.coordinator

data class OperationalSyncSessionCallbacks(
    val hasActiveSession: () -> Boolean,
    val isBootstrapped: () -> Boolean,
    val reauthenticateOfflineSession: suspend () -> Unit
)
