package com.example.quilacarne.data.sync.coordinator

data class RealtimeSyncState(
    val isOnline: () -> Boolean,
    val isServerAvailable: () -> Boolean,
    val isBootstrapped: () -> Boolean
)
