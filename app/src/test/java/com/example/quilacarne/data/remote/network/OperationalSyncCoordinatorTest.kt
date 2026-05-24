package com.example.quilacarne.data.remote.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class OperationalSyncCoordinatorTest {
    @Test
    fun routeEntryRunsOperationalAndMenuSyncForOrderScreen() = runTest {
        val fixture = Fixture(this)

        fixture.coordinator.onRouteChanged("order_add/{tableId}/{orderId}?pendingWaiterId={pendingWaiterId}")
        runCurrent()

        assertEquals(listOf("coordinator-screen-order-add-entered"), fixture.operationalReasons)
        assertEquals(listOf("coordinator-screen-order-add-entered"), fixture.menuReasons)
    }

    @Test
    fun actionSyncDoesNotRunInParallel() = runTest {
        val fixture = Fixture(this).apply {
            syncDelayMs = 1_000L
        }

        fixture.coordinator.onRouteChanged("tables")
        fixture.coordinator.requestSyncAfterAction("table-status-changed")
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, fixture.maxConcurrentSyncs)
        assertEquals(1, fixture.operationalReasons.size)
    }

    @Test
    fun offlineStateSkipsScreenAndActionSync() = runTest {
        val fixture = Fixture(this).apply {
            online = false
            serverAvailable = false
        }

        fixture.coordinator.onRouteChanged("tables")
        fixture.coordinator.requestSyncAfterAction("manual-refresh")
        runCurrent()

        assertTrue(fixture.operationalReasons.isEmpty())
        assertTrue(fixture.menuReasons.isEmpty())
    }

    @Test
    fun pollingStopsAfterLeavingOperationalScreen() = runTest {
        val fixture = Fixture(this)

        fixture.coordinator.tick()
        runCurrent()
        fixture.clearSyncCalls()

        fixture.coordinator.onRouteChanged("tables")
        runCurrent()
        fixture.clearSyncCalls()

        fixture.now = 31_000L
        fixture.coordinator.tick()
        runCurrent()

        assertEquals(listOf("coordinator-polling-tables"), fixture.operationalReasons)

        fixture.coordinator.onRouteChanged("main")
        assertFalse(fixture.coordinator.isPollingActive())
        fixture.clearSyncCalls()

        fixture.now = 62_000L
        fixture.coordinator.tick()
        runCurrent()

        assertTrue(fixture.operationalReasons.isEmpty())
        assertTrue(fixture.menuReasons.isEmpty())
    }

    @Test
    fun networkErrorPreventsPollingSync() = runTest {
        val fixture = Fixture(this).apply {
            serverReachable = false
        }

        fixture.coordinator.onRouteChanged("tables")
        runCurrent()
        fixture.clearSyncCalls()

        fixture.now = 31_000L
        fixture.coordinator.tick()
        runCurrent()

        assertTrue(fixture.operationalReasons.isEmpty())
        assertFalse(fixture.serverAvailable)
    }

    private class Fixture(
        private val scope: kotlinx.coroutines.CoroutineScope
    ) {
        var online = true
        var serverAvailable = true
        var serverReachable = true
        var activeSession = true
        var bootstrapped = true
        var now = 0L
        var syncDelayMs = 0L
        var maxConcurrentSyncs = 0

        val operationalReasons = mutableListOf<String>()
        val menuReasons = mutableListOf<String>()

        private var activeSyncs = 0

        val coordinator = OperationalSyncCoordinator(
            scope = scope,
            network = OperationalSyncNetworkCallbacks(
                refreshNetwork = {},
                isOnline = { online },
                isServerAvailable = { serverAvailable },
                updateServerAvailability = { available ->
                    serverAvailable = available
                },
                isServerReachable = { serverReachable }
            ),
            session = OperationalSyncSessionCallbacks(
                hasActiveSession = { activeSession },
                isBootstrapped = { bootstrapped },
                reauthenticateOfflineSession = {}
            ),
            syncActions = OperationalSyncActions(
                syncAllLocalData = {
                    trackSync {
                        Result.success(Unit)
                    }
                },
                syncOperationalData = { reason ->
                    trackSync {
                        operationalReasons.add(reason)
                        Result.success(Unit)
                    }
                },
                syncMenu = { reason ->
                    trackSync {
                        menuReasons.add(reason)
                        Result.success(Unit)
                    }
                }
            ),
            timing = OperationalSyncTiming(
                nowMillis = { now },
                connectionCheckIntervalMs = 15_000L,
                pollingIntervalMs = 30_000L
            )
        )

        fun clearSyncCalls() {
            operationalReasons.clear()
            menuReasons.clear()
        }

        private suspend fun trackSync(block: () -> Result<Unit>): Result<Unit> {
            activeSyncs += 1
            maxConcurrentSyncs = max(maxConcurrentSyncs, activeSyncs)
            return try {
                if (syncDelayMs > 0L) {
                    delay(syncDelayMs)
                }
                block()
            } finally {
                activeSyncs -= 1
            }
        }
    }
}
