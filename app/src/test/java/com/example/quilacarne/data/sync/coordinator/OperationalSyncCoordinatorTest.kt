package com.example.quilacarne.data.sync.coordinator

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

}
