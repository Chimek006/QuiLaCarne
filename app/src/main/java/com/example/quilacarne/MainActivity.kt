package com.example.quilacarne

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
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
import com.example.quilacarne.data.remote.network.OperationalSyncCoordinator
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.repository.SyncRepository
import com.example.quilacarne.ui.i18n.AppLanguageStore
import com.example.quilacarne.ui.screens.*
import com.example.quilacarne.ui.theme.QuiLaCarneTheme
import com.example.quilacarne.ui.viewmodels.TablesViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private var operationalSyncCoordinator: OperationalSyncCoordinator? = null

    companion object {
        lateinit var networkMonitor: NetworkMonitor
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        networkMonitor =
            NetworkMonitor(applicationContext)

        RetrofitClient.init(applicationContext)
        AppLanguageStore.init(applicationContext)
        startOperationalSyncCoordinator()

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

                DisposableEffect(navController) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        operationalSyncCoordinator?.onRouteChanged(destination.route)
                    }

                    navController.addOnDestinationChangedListener(listener)
                    operationalSyncCoordinator?.onRouteChanged(navController.currentDestination?.route)

                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                        operationalSyncCoordinator?.onRouteChanged(null)
                    }
                }

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
        operationalSyncCoordinator?.stop()
        operationalSyncCoordinator = null
        super.onDestroy()
    }

    private fun startOperationalSyncCoordinator() {
        val db = AppDatabase.getDatabase(applicationContext)
        val tokenManager = TokenManager(applicationContext)
        val syncRepository = SyncRepository(db, applicationContext)

        val coordinator = OperationalSyncCoordinator(
            scope = lifecycleScope,
            refreshNetwork = { networkMonitor.refresh() },
            isOnline = { networkMonitor.isOnline.value },
            isServerAvailable = { networkMonitor.isServerAvailable.value },
            updateServerAvailability = { isAvailable ->
                networkMonitor.updateServerAvailability(isAvailable)
            },
            hasActiveSession = { !tokenManager.getAccessToken().isNullOrBlank() },
            isBootstrapped = { tokenManager.isBootstrapped() },
            isServerReachable = { isServerReachable() },
            reauthenticateOfflineSession = {
                reauthenticateOfflineSession(db, tokenManager)
            },
            syncAllLocalData = {
                syncRepository.syncAllLocalData(clearBeforeSync = false)
            },
            syncOperationalData = { reason ->
                syncRepository.syncOperationalData(reason)
            },
            syncMenu = { _ ->
                syncRepository.syncMenu()
            }
        )
        operationalSyncCoordinator = coordinator

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                coordinator.start()
                try {
                    awaitCancellation()
                } finally {
                    coordinator.pause()
                }
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
