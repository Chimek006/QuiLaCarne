package com.example.quilacarne.data.remote.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkIssueResolverTest {
    @Test
    fun noInternetWinsOverServerState() {
        assertEquals(
            ConnectionIssue.NoInternet,
            NetworkIssueResolver.resolveConnectionIssue(
                isOnline = false,
                isServerAvailable = true
            )
        )
        assertEquals(
            ConnectionIssue.NoInternet,
            NetworkIssueResolver.resolveConnectionIssue(
                isOnline = false,
                isServerAvailable = false
            )
        )
    }

    @Test
    fun onlineButServerUnavailableReturnsNoServer() {
        assertEquals(
            ConnectionIssue.NoServer,
            NetworkIssueResolver.resolveConnectionIssue(
                isOnline = true,
                isServerAvailable = false
            )
        )
    }

    @Test
    fun onlineAndServerAvailableReturnsNone() {
        assertEquals(
            ConnectionIssue.None,
            NetworkIssueResolver.resolveConnectionIssue(
                isOnline = true,
                isServerAvailable = true
            )
        )
    }
}
