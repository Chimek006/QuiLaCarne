package com.example.quilacarne.data.remote.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OperationalSyncNetworkCallbacks(
    val refreshNetwork: () -> Unit,
    val isOnline: () -> Boolean,
    val isServerAvailable: () -> Boolean,
    val updateServerAvailability: (Boolean) -> Unit,
    val isServerReachable: suspend () -> Boolean
)

data class OperationalSyncSessionCallbacks(
    val hasActiveSession: () -> Boolean,
    val isBootstrapped: () -> Boolean,
    val reauthenticateOfflineSession: suspend () -> Unit
)

data class OperationalSyncActions(
    val syncAllLocalData: suspend () -> Result<Unit>,
    val syncOperationalData: suspend (String) -> Result<Unit>,
    val syncMenu: suspend (String) -> Result<Unit>
)

data class OperationalSyncTiming(
    val nowMillis: () -> Long = { System.currentTimeMillis() },
    val delayMillis: suspend (Long) -> Unit = { delay(it) },
    val connectionCheckIntervalMs: Long = DEFAULT_CONNECTION_CHECK_INTERVAL_MS,
    val pollingIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    companion object {
        const val DEFAULT_CONNECTION_CHECK_INTERVAL_MS = 15_000L
        const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}

class OperationalSyncCoordinator(
    private val scope: CoroutineScope,
    private val network: OperationalSyncNetworkCallbacks,
    private val session: OperationalSyncSessionCallbacks,
    private val syncActions: OperationalSyncActions,
    private val timing: OperationalSyncTiming = OperationalSyncTiming()
) {
    private var coordinatorJob: Job? = null
    private var syncJob: Job? = null
    private var wasServerAvailable = false
    private var hadActiveSession = false
    private var lastPollAt = 0L

    @Volatile
    private var activeTarget: OperationalSyncTarget? = null

    fun start(): Job {
        coordinatorJob?.takeIf { it.isActive }?.let { return it }

        coordinatorJob = scope.launch {
            while (isActive) {
                tick()
                timing.delayMillis(timing.connectionCheckIntervalMs)
            }
        }

        return coordinatorJob!!
    }

    fun stop() {
        pause()
        activeTarget = null
    }

    fun pause() {
        coordinatorJob?.cancel()
        coordinatorJob = null
        syncJob?.cancel()
        syncJob = null
    }

    fun onRouteChanged(route: String?) {
        val nextTarget = OperationalSyncTarget.fromRoute(route)
        val previousTarget = activeTarget
        activeTarget = nextTarget

        if (nextTarget != null && nextTarget != previousTarget) {
            requestScreenSync(nextTarget)
        }
    }

    fun requestSyncAfterAction(reason: String) {
        val target = activeTarget ?: OperationalSyncTarget.OPERATIONAL_ONLY
        requestTargetSync("action-$reason", target)
    }

    fun isPollingActive(): Boolean {
        return activeTarget != null
    }

    internal suspend fun tick() {
        network.refreshNetwork()

        val serverAvailable = if (network.isOnline()) {
            runCatching { network.isServerReachable() }
                .getOrElse { error ->
                    Log.w(TAG, "Server reachability check failed: ${error.message}")
                    false
                }
        } else {
            false
        }

        network.updateServerAvailability(serverAvailable)

        val activeSession = session.hasActiveSession()
        val bootstrapped = session.isBootstrapped()
        val shouldRefreshSession = serverAvailable &&
            activeSession &&
            bootstrapped &&
            (!wasServerAvailable || !hadActiveSession)

        if (shouldRefreshSession) {
            lastPollAt = timing.nowMillis()
            requestFullSync("session-or-connection-restored")
        }

        val target = activeTarget
        val shouldPoll = serverAvailable &&
            activeSession &&
            bootstrapped &&
            target != null &&
            timing.nowMillis() - lastPollAt >= timing.pollingIntervalMs

        if (shouldPoll) {
            target?.let { pollingTarget ->
                lastPollAt = timing.nowMillis()
                requestTargetSync("polling-${pollingTarget.key}", pollingTarget)
            }
        }

        wasServerAvailable = serverAvailable
        hadActiveSession = activeSession
    }

    private fun requestScreenSync(target: OperationalSyncTarget) {
        requestTargetSync("screen-${target.key}-entered", target)
    }

    private fun requestFullSync(reason: String) {
        if (!canSync() || syncJob?.isActive == true) {
            Log.d(TAG, "Full sync skipped reason=$reason")
            return
        }

        syncJob = scope.launch {
            Log.d(TAG, "Full REST sync started reason=$reason")
            runCatching {
                session.reauthenticateOfflineSession()
                syncActions.syncAllLocalData().getOrThrow()
            }.onSuccess {
                Log.d(TAG, "Full REST sync finished reason=$reason")
            }.onFailure { error ->
                Log.w(TAG, "Full REST sync failed reason=$reason message=${error.message}")
            }
        }
    }

    private fun requestTargetSync(
        reason: String,
        target: OperationalSyncTarget
    ) {
        if (!canSync()) {
            Log.d(TAG, "REST sync skipped reason=$reason")
            return
        }

        if (syncJob?.isActive == true) {
            Log.d(TAG, "REST sync already running reason=$reason")
            return
        }

        syncJob = scope.launch {
            if (target.includeOperational) {
                syncActions.syncOperationalData("coordinator-$reason")
                    .onSuccess {
                        Log.d(TAG, "Operational REST sync finished reason=$reason")
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Operational REST sync failed reason=$reason message=${error.message}")
                    }
            }

            if (target.includeMenu) {
                syncActions.syncMenu("coordinator-$reason")
                    .onSuccess {
                        Log.d(TAG, "Menu REST sync finished reason=$reason")
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Menu REST sync failed reason=$reason message=${error.message}")
                    }
            }
        }
    }

    private fun canSync(): Boolean {
        return network.isOnline() &&
            network.isServerAvailable() &&
            session.hasActiveSession() &&
            session.isBootstrapped()
    }

    private data class OperationalSyncTarget(
        val key: String,
        val includeOperational: Boolean,
        val includeMenu: Boolean
    ) {
        companion object {
            val OPERATIONAL_ONLY = OperationalSyncTarget(
                key = "operational",
                includeOperational = true,
                includeMenu = false
            )

            fun fromRoute(route: String?): OperationalSyncTarget? {
                val normalizedRoute = route
                    ?.substringBefore("?")
                    ?.trim()
                    ?: return null

                return when {
                    normalizedRoute == "tables" -> OPERATIONAL_ONLY.copy(key = "tables")
                    normalizedRoute.startsWith("table/") -> OPERATIONAL_ONLY.copy(key = "table-detail")
                    normalizedRoute.startsWith("report_client/") -> OPERATIONAL_ONLY.copy(key = "report-client")
                    normalizedRoute == "menu" -> OperationalSyncTarget(
                        key = "menu",
                        includeOperational = false,
                        includeMenu = true
                    )
                    normalizedRoute.startsWith("dish_detail/") -> OperationalSyncTarget(
                        key = "dish-detail",
                        includeOperational = false,
                        includeMenu = true
                    )
                    normalizedRoute.startsWith("order_add/") -> OperationalSyncTarget(
                        key = "order-add",
                        includeOperational = true,
                        includeMenu = true
                    )
                    else -> null
                }
            }
        }
    }

    private companion object {
        const val TAG = "OPERATIONAL_SYNC"
    }
}
