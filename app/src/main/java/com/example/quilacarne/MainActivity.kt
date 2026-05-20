package com.example.quilacarne

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.remote.dto.LoginRequest
import com.example.quilacarne.data.remote.network.NetworkMonitor
import com.example.quilacarne.data.remote.network.RealtimeSyncClient
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.network.WaiterAssignmentNotificationHelper
import com.example.quilacarne.data.repository.SyncRepository
import com.example.quilacarne.ui.i18n.AppLanguageStore
import com.example.quilacarne.ui.screens.*
import com.example.quilacarne.ui.theme.QuiLaCarneTheme
import com.example.quilacarne.ui.viewmodels.TablesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private var realtimeSyncClient: RealtimeSyncClient? = null

    companion object {
        lateinit var networkMonitor: NetworkMonitor
        private const val CONNECTION_CHECK_INTERVAL_MS = 15_000L
        private const val FALLBACK_POLL_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        networkMonitor =
            NetworkMonitor(applicationContext)

        RetrofitClient.init(applicationContext)
        AppLanguageStore.init(applicationContext)
        startConnectionWatcher()

        thread {

            try {

                val db =
                    AppDatabase.getDatabase(
                        applicationContext
                    )

                db.openHelper.writableDatabase

                Log.d(
                    "QUI_LA_CARNE",
                    "Database connection forced successfully"
                )

            } catch (e: Exception) {

                Log.e(
                    "QUI_LA_CARNE",
                    "Failed to force database connection: ${e.message}"
                )
            }
        }

        setContent {

            QuiLaCarneTheme {

                val navController =
                    rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "login"
                ) {

                    composable("login") {
                        LoginScreen(navController)
                    }

                    composable("main") {
                        MainScreen(navController)
                    }

                    composable("tables") {

                        val context =
                            LocalContext.current

                        val db =
                            remember(context) {
                                AppDatabase.getDatabase(context)
                            }

                        val repository =
                            remember(db) {
                                SyncRepository(db, context.applicationContext)
                            }

                        val viewModel =
                            remember(repository) {
                                TablesViewModel(repository)
                            }

                        TablesScreen(
                            navController,
                            viewModel
                        )
                    }

                    composable("menu") {
                        MenuScreen(navController)
                    }

                    composable(
                        "table/{tableId}/{tableName}/{status}"
                    ) { backStackEntry ->

                        val tableIdString =
                            backStackEntry
                                .arguments
                                ?.getString("tableId")

                        val tableName =
                            decodeNavArgument(
                                backStackEntry
                                    .arguments
                                    ?.getString("tableName")
                                    ?: "Stolik"
                            )

                        val status =
                            decodeNavArgument(
                                backStackEntry
                                    .arguments
                                    ?.getString("status")
                                    ?: "Wolny"
                            )

                        val tableId = try {

                            UUID.fromString(tableIdString)

                        } catch (e: Exception) {

                            UUID.nameUUIDFromBytes(
                                tableIdString
                                    ?.toByteArray()
                                    ?: ByteArray(0)
                            )
                        }

                        TableDetailScreen(
                            navController = navController,
                            tableId = tableId,
                            tableName = tableName,
                            tableStatus = status
                        )
                    }

                    composable("sync") {
                        SyncScreen(navController)
                    }

                    composable(
                        route = "order_add/{tableId}/{orderId}?pendingWaiterId={pendingWaiterId}",
                        arguments = listOf(
                            navArgument("pendingWaiterId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->

                        val tableIdString =
                            backStackEntry
                                .arguments
                                ?.getString("tableId")
                                ?: ""

                        val orderIdString =
                            backStackEntry
                                .arguments
                                ?.getString("orderId")
                                ?: ""

                        val tableId = try {

                            UUID.fromString(tableIdString)

                        } catch (e: Exception) {

                            UUID.nameUUIDFromBytes(
                                tableIdString.toByteArray()
                            )
                        }

                        val orderId = try {

                            UUID.fromString(orderIdString)

                        } catch (e: Exception) {

                            UUID.nameUUIDFromBytes(
                                orderIdString.toByteArray()
                            )
                        }

                        val pendingWaiterIdString =
                            backStackEntry
                                .arguments
                                ?.getString("pendingWaiterId")

                        val pendingWaiterId =
                            pendingWaiterIdString
                                ?.takeIf { it.isNotBlank() && it != "null" }
                                ?.let { rawWaiterId ->
                                    try {
                                        UUID.fromString(rawWaiterId)
                                    } catch (e: Exception) {
                                        UUID.nameUUIDFromBytes(rawWaiterId.toByteArray())
                                    }
                                }

                        OrderAddScreen(
                            navController = navController,
                            tableId = tableId,
                            orderId = orderId,
                            pendingWaiterId = pendingWaiterId
                        )
                    }

                    composable(
                        route = "report_client/{tableId}"
                    ) { backStackEntry ->

                        val tableIdString =
                            backStackEntry
                                .arguments
                                ?.getString("tableId")
                                ?: ""

                        val tableId = try {

                            UUID.fromString(tableIdString)

                        } catch (e: Exception) {

                            UUID.nameUUIDFromBytes(
                                tableIdString.toByteArray()
                            )
                        }

                        ReportClientScreen(
                            navController = navController,
                            tableId = tableId
                        )
                    }

                    composable(
                        route = "dish_detail/{dishId}"
                    ) { backStackEntry ->

                        val dishIdString =
                            backStackEntry
                                .arguments
                                ?.getString("dishId")
                                ?: ""

                        val dishId = try {

                            UUID.fromString(dishIdString)

                        } catch (e: Exception) {

                            UUID.nameUUIDFromBytes(
                                dishIdString.toByteArray()
                            )
                        }

                        DishDetailScreen(
                            dishId = dishId,
                            navController = navController,
                            viewModel = viewModel()
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        realtimeSyncClient?.shutdown()
        realtimeSyncClient = null
        super.onDestroy()
    }

    private fun startConnectionWatcher() {
        val db = AppDatabase.getDatabase(applicationContext)
        val tokenManager = TokenManager(applicationContext)
        val syncRepository = SyncRepository(db, applicationContext)
        val notificationHelper = WaiterAssignmentNotificationHelper(
            context = applicationContext,
            tokenManager = tokenManager,
            database = db
        )
        val realtimeClient = RealtimeSyncClient(
            tokenManager = tokenManager,
            syncRepository = syncRepository,
            scope = lifecycleScope,
            waiterNotificationHelper = notificationHelper
        )
        realtimeSyncClient = realtimeClient

        lifecycleScope.launch {
            var wasServerAvailable = false
            var lastFallbackPollAt = 0L

            while (true) {
                networkMonitor.refresh()

                val serverAvailable = if (networkMonitor.isOnline.value) {
                    isServerReachable()
                } else {
                    false
                }

                networkMonitor.updateServerAvailability(serverAvailable)

                val hasActiveSession = !tokenManager.getAccessToken().isNullOrBlank()
                if (serverAvailable && hasActiveSession) {
                    realtimeClient.start()
                } else {
                    realtimeClient.stop()
                }

                val shouldSync = serverAvailable &&
                    !wasServerAvailable &&
                    tokenManager.isBootstrapped() &&
                    hasActiveSession

                if (shouldSync) {
                    runCatching {
                        reauthenticateOfflineSession(db, tokenManager)
                        syncRepository.syncAllLocalData(clearBeforeSync = false).getOrThrow()
                        lastFallbackPollAt = System.currentTimeMillis()
                    }.onFailure { error ->
                        Log.e("CONNECTION_SYNC", "Background sync failed: ${error.message}")
                    }
                }

                val nowMillis = System.currentTimeMillis()
                val shouldFallbackPoll = serverAvailable &&
                    hasActiveSession &&
                    tokenManager.isBootstrapped() &&
                    (!realtimeClient.isConfigured() || !realtimeClient.isConnected()) &&
                    nowMillis - lastFallbackPollAt >= FALLBACK_POLL_INTERVAL_MS

                if (shouldFallbackPoll) {
                    lastFallbackPollAt = nowMillis
                    Log.d(
                        "CENTRAL_SYNC",
                        "Fallback polling sync started websocketConfigured=${realtimeClient.isConfigured()} " +
                            "websocketConnected=${realtimeClient.isConnected()}"
                    )
                    syncRepository.syncOperationalData("central-fallback-polling")
                        .onFailure { error ->
                            Log.w("CENTRAL_SYNC", "Fallback polling sync failed: ${error.message}")
                        }
                }

                wasServerAvailable = serverAvailable
                delay(CONNECTION_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun isServerReachable(): Boolean {
        return try {
            RetrofitClient.authService.csrf()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeNavArgument(value: String): String {
        return Uri.decode(value).replace("+", " ")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private suspend fun reauthenticateOfflineSession(
        db: AppDatabase,
        tokenManager: TokenManager
    ) {
        val accessToken = tokenManager.getAccessToken()

        if (accessToken?.startsWith("offline_token_") != true) {
            return
        }

        val username = tokenManager.getCurrentUsername() ?: return
        val user = db.userDao().getUserByUsername(username) ?: return
        if (user.password.isBlank()) return

        val response = RetrofitClient.authService.login(
            LoginRequest(
                username = user.username,
                password = user.password
            )
        )

        val data = response.body()?.data

        if (response.isSuccessful && response.body()?.isSuccess == true && data != null) {
            tokenManager.saveTokens(data.token, data.refreshToken)
        }
    }
}
