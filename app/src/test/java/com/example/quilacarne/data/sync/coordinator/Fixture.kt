package com.example.quilacarne.data.sync.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.math.max

internal class Fixture(
    private val scope: CoroutineScope
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
