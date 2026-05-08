package com.example.quilacarne.data.remote.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionIssue {
    None,
    NoInternet,
    NoServer
}

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(isOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _isServerAvailable = MutableStateFlow(true)
    val isServerAvailable: StateFlow<Boolean> = _isServerAvailable

    private val _connectionIssue = MutableStateFlow(
        if (_isOnline.value) ConnectionIssue.None else ConnectionIssue.NoInternet
    )
    val connectionIssue: StateFlow<ConnectionIssue> = _connectionIssue

    init {
        observeNetworkChanges()
    }

    @SuppressLint("MissingPermission")
    private fun isOnlineNow(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun refresh() {
        _isOnline.value = isOnlineNow()
        updateConnectionIssue()
    }

    fun updateServerAvailability(isAvailable: Boolean) {
        _isServerAvailable.value = isAvailable
        updateConnectionIssue()
    }

    @SuppressLint("MissingPermission")
    private fun observeNetworkChanges() {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        refresh()
                    }

                    override fun onLost(network: Network) {
                        refresh()
                        updateServerAvailability(false)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        refresh()
                    }
                }
            )
        }
    }

    private fun updateConnectionIssue() {
        _connectionIssue.value = when {
            !_isOnline.value -> ConnectionIssue.NoInternet
            !_isServerAvailable.value -> ConnectionIssue.NoServer
            else -> ConnectionIssue.None
        }
    }
}
