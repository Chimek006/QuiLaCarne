package com.example.quilacarne.data.remote.network

internal object NetworkIssueResolver {
    fun resolveConnectionIssue(
        isOnline: Boolean,
        isServerAvailable: Boolean
    ): ConnectionIssue {
        return when {
            !isOnline -> ConnectionIssue.NoInternet
            !isServerAvailable -> ConnectionIssue.NoServer
            else -> ConnectionIssue.None
        }
    }
}
