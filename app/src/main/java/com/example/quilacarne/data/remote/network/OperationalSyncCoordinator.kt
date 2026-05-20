package com.example.quilacarne.data.remote.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OperationalSyncCoordinator(
    private val scope: CoroutineScope,
    private val refreshNetwork: () -> Unit,
    private val isOnline: () -> Boolean,
    private val isServerAvailable: () -> Boolean,
    private val updateServerAvailability: (Boolean) -> Unit,
    private val hasActiveSession: () -> Boolean,
    private val isBootstrapped: () -> Boolean,
    private val isServerReachable: suspend () -> Boolean,
    private val reauthenticateOfflineSession: suspend () -> Unit,
    private val syncAllLocalData: suspend () -> Result<Unit>,
    private val syncOperationalData: suspend (String) -> Result<Unit>,
    private val syncMenu: suspend (String) -> Result<Unit>,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val connectionCheckIntervalMs: Long = DEFAULT_CONNECTION_CHECK_INTERVAL_MS,
    private val pollingIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
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
                delayMillis(connectionCheckIntervalMs)
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
        refreshNetwork()

        val serverAvailable = if (isOnline()) {
            runCatching { isServerReachable() }
                .getOrElse { error ->
                    Log.w(TAG, "Server reachability check failed: ${error.message}")
                    false
                }
        } else {
            false
        }

        updateServerAvailability(serverAvailable)

        val activeSession = hasActiveSession()
        val bootstrapped = isBootstrapped()
        val shouldRefreshSession = serverAvailable &&
            activeSession &&
            bootstrapped &&
            (!wasServerAvailable || !hadActiveSession)

        if (shouldRefreshSession) {
            lastPollAt = nowMillis()
            requestFullSync("session-or-connection-restored")
        }

        val target = activeTarget
        val shouldPoll = serverAvailable &&
            activeSession &&
            bootstrapped &&
            target != null &&
            nowMillis() - lastPollAt >= pollingIntervalMs

        if (shouldPoll && target != null) {
            lastPollAt = nowMillis()
            requestTargetSync("polling-${target.key}", target)
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
                reauthenticateOfflineSession()
                syncAllLocalData().getOrThrow()
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
                syncOperationalData("coordinator-$reason")
                    .onSuccess {
                        Log.d(TAG, "Operational REST sync finished reason=$reason")
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Operational REST sync failed reason=$reason message=${error.message}")
                    }
            }

            if (target.includeMenu) {
                syncMenu("coordinator-$reason")
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
        return isOnline() &&
            isServerAvailable() &&
            hasActiveSession() &&
            isBootstrapped()
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
        const val DEFAULT_CONNECTION_CHECK_INTERVAL_MS = 15_000L
        const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}
