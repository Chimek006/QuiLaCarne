package com.example.quilacarne.data.sync.coordinator

data class OperationalSyncNetworkCallbacks(
    val refreshNetwork: () -> Unit,
    val isOnline: () -> Boolean,
    val isServerAvailable: () -> Boolean,
    val updateServerAvailability: (Boolean) -> Unit,
    val isServerReachable: suspend () -> Boolean
)
