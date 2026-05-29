package com.example.quilacarne.data.repository.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.local.entities.isActiveForTable
import com.example.quilacarne.data.local.entities.isInProgressForTable
import com.example.quilacarne.data.local.entities.isOccupiedForTable
import com.example.quilacarne.data.remote.dto.request.ReportCreateRequest
import com.example.quilacarne.data.remote.dto.request.ReservationDishRequest
import com.example.quilacarne.data.remote.dto.response.BootstrapResponse
import com.example.quilacarne.data.remote.dto.response.ReservationSyncDto
import com.example.quilacarne.data.remote.dto.response.TableDto
import com.example.quilacarne.data.remote.dto.response.WebSocketEvent
import com.example.quilacarne.data.remote.dto.response.values
import com.example.quilacarne.data.remote.network.RemoteTokenStore
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.utils.ReservationTimeUtils
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import java.util.UUID

class SyncRepository(
    private val database: AppDatabase,
    private val appContext: Context? = null
) {
    companion object {
        private val operationalSyncMutex = Mutex()
        private val _isOperationalSyncRunning = MutableStateFlow(false)
        val isOperationalSyncRunning: StateFlow<Boolean> = _isOperationalSyncRunning
        private const val PERSONNEL_UPDATES_TOPIC = "/topic/personnel/updates"
        private val CURRENT_USER_SESSION_CLEAR_EVENT_TYPES = setOf("CREATED", "UPDATED")
    }

    private val dishService = RetrofitClient.dishService
    private val tableService = RetrofitClient.tableService
    private val orderService = RetrofitClient.orderService
    private val tokenStore: RemoteTokenStore? = appContext?.let { RemoteTokenStore(it) }
    private val tokenManager: TokenManager? = appContext?.let { TokenManager(it) }
    private val gson = Gson()
    private val pendingRequestRepository: PendingRequestRepository? = appContext?.let {
        PendingRequestRepository(database.pendingRequestDao(), it, gson)
    }
    private val dishImageCache by lazy { DishImageCache(appContext) }

    fun getTablesFlow(): Flow<List<RestaurantTableEntity>> {
        return database.restaurantTableDao().getAllTablesFlow()
    }

    fun getTableStatusesFlow(): Flow<List<TableStatusEntity>> {
        return database.tableStatusDao().getAllStatuses()
    }

    fun getOrdersFlow(): Flow<List<OrderEntity>> {
        return database.orderDao().getAllOrders()
    }

    fun getOrderReservationToken(orderId: UUID): String? {
        return tokenStore?.getOrderReservationToken(orderId)
    }

    fun getReservationWaiterId(reservationToken: String): UUID? {
        return tokenStore?.getReservationWaiterId(reservationToken)
    }

    fun getReservationsFlow(): Flow<List<ReservationEntity>> {
        return database.reservationDao().getReservationsFlow()
    }

    fun getUsersFlow(): Flow<List<UsersEntity>> {
        return database.userDao().getAllUsersFlow()
    }

    fun getOperationalSyncRunningFlow(): StateFlow<Boolean> {
        return isOperationalSyncRunning
    }

    private val webSocketSyncHandler by lazy {
        WebSocketSyncHandler(
            database = database,
            tokenStore = tokenStore,
            gson = gson,
            cacheDishImage = dishImageCache::cacheDishImage
        )
    }

    suspend fun applyWebSocketEvent(
        topic: String,
        rawMessage: String
    ): Result<Unit> = webSocketSyncHandler.applyWebSocketEvent(topic, rawMessage)

    suspend fun clearCurrentSessionForPersonnelUpdateIfNeeded(
        topic: String,
        rawMessage: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            clearCurrentSessionForMatchingPersonnelEvent(topic, rawMessage)
        }.onFailure { error ->
            Log.w("QLC_WS_EVENT", "Nie udalo sie sprawdzic eventu personnel: ${error.message}")
        }.getOrDefault(false)
    }

    suspend fun syncAllLocalData(clearBeforeSync: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val preservedOfflineUsers = database.userDao()
                .getAllUsersOnce()
                .filter { it.password.isNotBlank() }
            val preservedPasswords = preservedOfflineUsers.toPreservedPasswordMap()
            val currentUsername = tokenManager
                ?.getCurrentUsername()
                ?.trim()
            val currentOfflineUser = currentUsername
                ?.let { username -> preservedOfflineUsers.findByUsername(username) }

            if (clearBeforeSync) {
                ensureServerReachable()
                database.clearAllTables()
                restoreOfflineLoginUsers(preservedOfflineUsers)
            }

            syncMenu().getOrThrow()
            syncTables().getOrThrow()
            syncUsers(preservedPasswords)
            restoreOfflineLoginUsers(listOfNotNull(currentOfflineUser))
            syncReservations()
            syncOrdersAndItems()
        }
    }

    suspend fun syncOperationalData(reason: String = "unspecified"): Result<Unit> = withContext(Dispatchers.IO) {
        if (operationalSyncMutex.isLocked) {
            Log.d("SYNC_FLOW", "Waiting for operational sync lock reason=$reason")
        }

        operationalSyncMutex.withLock {
            _isOperationalSyncRunning.value = true
            try {
                Log.d("SYNC_FLOW", "Operational sync started reason=$reason")
                runCatching {
                    val snapshot = fetchOperationalSnapshot()
                    applyOperationalSnapshot(snapshot)
                    persistOperationalSnapshotTokens(snapshot)
                }.also { result ->
                    result
                        .onSuccess {
                            Log.d("SYNC_FLOW", "Operational sync finished reason=$reason")
                        }
                        .onFailure { error ->
                            Log.w("SYNC_FLOW", "Operational sync failed reason=$reason message=${error.message}")
                        }
                }
            } finally {
                _isOperationalSyncRunning.value = false
            }
        }
    }

    private suspend fun fetchOperationalSnapshot(
        preservedPasswords: Map<String, String> = emptyMap()
    ): OperationalSyncSnapshot = coroutineScope {
        val now = getCurrentTimestamp()
        val tables = async { fetchTablesSnapshot(now) }
        val reservations = async { fetchReservationsSnapshot() }
        val bootstrap = fetchBootstrap("BĹ‚Ä…d pobierania manifestu danych operacyjnych")
        val users = async { fetchUsersSnapshot(bootstrap, preservedPasswords) }
        val ordersAndItems = async { fetchOrdersAndItemsSnapshot(bootstrap) }

        OperationalSyncSnapshot(
            tables = tables.await(),
            users = users.await(),
            reservations = reservations.await(),
            ordersAndItems = ordersAndItems.await()
        )
    }

    private suspend fun applyOperationalSnapshot(snapshot: OperationalSyncSnapshot) {
        database.withTransaction {
            applyTablesSnapshot(snapshot.tables)
            applyUsersSnapshot(snapshot.users)
            applyReservationsSnapshot(snapshot.reservations)
            applyOrdersAndItemsSnapshot(snapshot.ordersAndItems)
            clearWaitersForReleasedTables()
        }
    }

    private suspend fun persistOperationalSnapshotTokens(snapshot: OperationalSyncSnapshot) {
        persistTableSnapshotTokens(snapshot.tables)
        persistUsersSnapshotTokens(snapshot.users)
        persistReservationSnapshotTokens(
            snapshot = snapshot.reservations,
            knownTableIds = snapshot.tables.tables.map { it.id }
        )
        persistOrderSnapshotTokens(snapshot.ordersAndItems)
    }

    private suspend fun ensureServerReachable() {
        try {
            Log.d("SYNC", "Checking csrf...")

            val response = RetrofitClient.authService.csrf()

            Log.d("SYNC", "Code: ${response.code()}")

            if (!response.isSuccessful) {
                error("Serwer API jest niedostepny")
            }

        } catch (e: Exception) {
            Log.e("SYNC", "CSRF failed", e)
            throw e
        }
    }

    suspend fun syncTables(): Result<Unit> {
        return try {
            syncTableStatuses().onFailure { error ->
                Log.w("SYNC", "Nie udało się zsynchronizować słownika statusów stolików: ${error.message}")
            }

            val now = getCurrentTimestamp()
            val dao = database.restaurantTableDao()

            var page = 1
            val allTablesEntities = mutableListOf<RestaurantTableEntity>()
            val tableStatusTokens = mutableSetOf<String>()
            val existingTablesById = dao.getAllTablesOnce().associateBy { it.id }
            val existingStatusTokensByTableId = dao
                .getAllTablesOnce()
                .associate { table ->
                    table.id to table.statusId?.let { statusId ->
                        database.tableStatusDao()
                            .getStatusByIdOnce(statusId)
                            ?.token
                    }
                }

            while (true) {
                val response = tableService.syncTables(page)

                if (!response.isSuccessful) {
                    Log.e("SYNC", "Błąd API stolików: ${response.code()}")
                    break
                }

                val body = response.body()
                val items: List<TableDto> = body?.data?.items.orEmpty()

                if (items.isEmpty()) break

                items.forEach { dto ->
                    val tableId = dto.token.toStableUUID()
                    val existingStatusToken = existingStatusTokensByTableId[tableId]
                    val remoteStatusToken = dto.currentStatusToken()
                    val statusDecision = TableStatusSyncLogic.resolveSyncedTableStatusDecision(
                        remoteStatusToken = remoteStatusToken,
                        existingStatusToken = existingStatusToken,
                        remoteUpdatedAt = dto.updatedAt,
                        existingUpdatedAt = existingTablesById[tableId]?.updatedAt
                    )
                    val statusToken = statusDecision.statusToken

                    Log.d(
                        "TABLE_STATUS_SYNC",
                        "tableId=$tableId remote=$remoteStatusToken local=$existingStatusToken " +
                            "final=$statusToken reason=${statusDecision.reason} " +
                            "remoteUpdatedAt=${dto.updatedAt} localUpdatedAt=${existingTablesById[tableId]?.updatedAt}"
                    )

                    if (!statusToken.isNullOrBlank()) {
                        tableStatusTokens.add(statusToken)
                    }

                    allTablesEntities.add(
                            RestaurantTableEntity(
                            id = tableId,
                            tableNumber = dto.tableNumber,
                            capacity = dto.capacity,
                            statusId = statusToken?.toStableUUID(),
                            createdAt = now,
                            updatedAt = dto.updatedAt.ifBlank { now }
                        )
                    )
                    tokenStore?.saveToken("table", tableId, dto.token)
                }

                page++
            }

            if (allTablesEntities.isNotEmpty()) {
                val protectedTables = protectTablesWithPendingStatus(allTablesEntities)
                database.withTransaction {
                    val knownStatusTokens = database.tableStatusDao()
                        .getAllStatusesOnce()
                        .map { it.token.uppercase() }
                        .toSet()

                    val fallbackStatuses = tableStatusTokens
                        .filter { it.uppercase() !in knownStatusTokens }
                        .map { token ->
                            TableStatusEntity(
                                id = token.toStableUUID(),
                                token = token,
                                namePl = token.toTableStatusName(),
                                nameEn = token.toTableStatusNameEn(),
                                createdAt = now,
                                updatedAt = now
                            )
                        }

                    if (fallbackStatuses.isNotEmpty()) {
                        database.tableStatusDao().insertAll(fallbackStatuses)
                    }

                    dao.insertTables(protectedTables)
                    dao.deleteTablesExcept(protectedTables.map { it.id })
                    clearWaitersForReleasedTables()
                }
                Log.d("SYNC", "Zapisano ${allTablesEntities.size} stolików do bazy")
            } else {
                Log.w("SYNC", "UWAGA: Brak stolików z API!")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Błąd stolików: ", e)
            Result.failure(e)
        }
    }

    private suspend fun syncTableStatuses(): Result<Unit> = runCatching {
        val now = getCurrentTimestamp()
        val responsePl = tableService.getStatusDictionary(lang = "pl")

        if (!responsePl.isSuccessful || responsePl.body()?.isSuccess != true) {
            throw IllegalStateException(responsePl.body()?.message ?: "Blad pobierania statusow")
        }

        val statusesPl = responsePl.body()
            ?.data
            .values()
            .orEmpty()

        val statusesEnByToken = tableService.getStatusDictionary(lang = "en")
            .takeIf { it.isSuccessful && it.body()?.isSuccess == true }
            ?.body()
            ?.data
            .values()
            .associate { dto -> dto.token to dto.name }
            .orEmpty()

        val statusesPlByToken = statusesPl.associateBy { it.token }
        val statusEntities = (statusesPlByToken.keys + statusesEnByToken.keys)
            .distinct()
            .map { token ->
                TableStatusEntity(
                    id = token.toStableUUID(),
                    token = token,
                    namePl = statusesPlByToken[token]?.name ?: token.toTableStatusName(),
                    nameEn = statusesEnByToken[token] ?: token.toTableStatusNameEn(),
                    createdAt = now,
                    updatedAt = now
                )
            }

        if (statusEntities.isNotEmpty()) {
            database.tableStatusDao().insertAll(statusEntities)
        }
    }

    private suspend fun fetchTablesSnapshot(now: String): TablesSyncSnapshot {
        val dictionaryStatuses = runCatching { fetchTableStatusEntities(now) }
            .onFailure { error ->
                Log.w("SYNC", "Nie udalo sie zsynchronizowac slownika statusow stolikow: ${error.message}")
            }
            .getOrDefault(emptyList())

        val dao = database.restaurantTableDao()
        val existingTables = dao.getAllTablesOnce()
        val existingTablesById = existingTables.associateBy { it.id }
        val existingStatusTokensByTableId = existingTables.associate { table ->
            table.id to table.statusId?.let { statusId ->
                database.tableStatusDao()
                    .getStatusByIdOnce(statusId)
                    ?.token
            }
        }

        var page = 1
        val tables = mutableListOf<RestaurantTableEntity>()
        val tableTokens = mutableListOf<Pair<UUID, String>>()
        val tableStatusTokens = mutableSetOf<String>()

        while (true) {
            val response = tableService.syncTables(page)
            val body = response.body()

            if (!response.isSuccessful || body?.isSuccess != true) {
                throw IllegalStateException(body?.message ?: "Blad API stolikow: ${response.code()}")
            }

            val items = body.data.items.orEmpty()
            if (items.isEmpty()) break

            items.forEach { dto ->
                val tableId = dto.token.toStableUUID()
                val existingStatusToken = existingStatusTokensByTableId[tableId]
                val remoteStatusToken = dto.currentStatusToken()
                val statusDecision = TableStatusSyncLogic.resolveSyncedTableStatusDecision(
                    remoteStatusToken = remoteStatusToken,
                    existingStatusToken = existingStatusToken,
                    remoteUpdatedAt = dto.updatedAt,
                    existingUpdatedAt = existingTablesById[tableId]?.updatedAt
                )
                val statusToken = statusDecision.statusToken

                Log.d(
                    "TABLE_STATUS_SYNC",
                    "tableId=$tableId remote=$remoteStatusToken local=$existingStatusToken " +
                        "final=$statusToken reason=${statusDecision.reason} " +
                        "remoteUpdatedAt=${dto.updatedAt} localUpdatedAt=${existingTablesById[tableId]?.updatedAt}"
                )

                if (!statusToken.isNullOrBlank()) {
                    tableStatusTokens.add(statusToken)
                }

                tables.add(
                    RestaurantTableEntity(
                        id = tableId,
                        tableNumber = dto.tableNumber,
                        capacity = dto.capacity,
                        statusId = statusToken?.toStableUUID(),
                        createdAt = now,
                        updatedAt = dto.updatedAt.ifBlank { now }
                    )
                )
                tableTokens.add(tableId to dto.token)
            }

            page++
        }

        val knownStatusTokens = (database.tableStatusDao().getAllStatusesOnce() + dictionaryStatuses)
            .map { it.token.uppercase(Locale.US) }
            .toSet()
        val fallbackStatuses = tableStatusTokens
            .filter { it.uppercase(Locale.US) !in knownStatusTokens }
            .map { token ->
                TableStatusEntity(
                    id = token.toStableUUID(),
                    token = token,
                    namePl = token.toTableStatusName(),
                    nameEn = token.toTableStatusNameEn(),
                    createdAt = now,
                    updatedAt = now
                )
            }

        return TablesSyncSnapshot(
            statuses = (dictionaryStatuses + fallbackStatuses).distinctBy { it.id },
            tables = tables,
            tableTokens = tableTokens
        )
    }

    private suspend fun fetchTableStatusEntities(now: String): List<TableStatusEntity> {
        val responsePl = tableService.getStatusDictionary(lang = "pl")

        if (!responsePl.isSuccessful || responsePl.body()?.isSuccess != true) {
            throw IllegalStateException(responsePl.body()?.message ?: "Blad pobierania statusow")
        }

        val statusesPl = responsePl.body()
            ?.data
            .values()
            .orEmpty()

        val statusesEnByToken = tableService.getStatusDictionary(lang = "en")
            .takeIf { it.isSuccessful && it.body()?.isSuccess == true }
            ?.body()
            ?.data
            .values()
            .associate { dto -> dto.token to dto.name }
            .orEmpty()

        val statusesPlByToken = statusesPl.associateBy { it.token }
        return (statusesPlByToken.keys + statusesEnByToken.keys)
            .distinct()
            .map { token ->
                TableStatusEntity(
                    id = token.toStableUUID(),
                    token = token,
                    namePl = statusesPlByToken[token]?.name ?: token.toTableStatusName(),
                    nameEn = statusesEnByToken[token] ?: token.toTableStatusNameEn(),
                    createdAt = now,
                    updatedAt = now
                )
            }
    }

    private suspend fun applyTablesSnapshot(snapshot: TablesSyncSnapshot) {
        val protectedTables = protectTablesWithPendingStatus(snapshot.tables)
        val pendingStatusTokens = protectedTables
            .mapNotNull { table -> table.statusId }
            .filter { statusId -> snapshot.statuses.none { it.id == statusId } }
            .mapNotNull { statusId ->
                database.tableStatusDao().getStatusByIdOnce(statusId)
            }
        val statuses = (snapshot.statuses + pendingStatusTokens).distinctBy { it.id }

        if (statuses.isNotEmpty()) {
            database.tableStatusDao().insertAll(statuses)
        }

        if (protectedTables.isNotEmpty()) {
            database.restaurantTableDao().insertTables(protectedTables)
            database.restaurantTableDao().deleteTablesExcept(protectedTables.map { it.id })
            Log.d("SYNC", "Zapisano ${protectedTables.size} stolikow do bazy")
        } else {
            Log.w("SYNC", "UWAGA: Brak stolikow z API!")
        }
    }

    private suspend fun protectTablesWithPendingStatus(
        tables: List<RestaurantTableEntity>
    ): List<RestaurantTableEntity> {
        val pendingStatusByTableId = pendingTableStatusTokensByTableId()
        if (pendingStatusByTableId.isEmpty()) return tables

        return tables.map { table ->
            pendingStatusByTableId[table.id]
                ?.let { statusToken ->
                    table.copy(statusId = statusToken.toStableUUID())
                }
                ?: table
        }
    }

    private suspend fun pendingTableStatusTokensByTableId(): Map<UUID, String> {
        val pendingStatuses = database.pendingRequestDao()
            .getPendingByEntityType(PendingRequestRepository.ENTITY_TABLE)
            .mapNotNull { pending ->
                val tableId = pending.entityLocalId
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@mapNotNull null
                val statusToken = PendingTableStatusLogic.statusTokenForAction(pending.optimisticAction)
                    ?: return@mapNotNull null

                tableId to statusToken
            }
            .toMap()
        return pendingStatuses
    }

    private fun persistTableSnapshotTokens(snapshot: TablesSyncSnapshot) {
        snapshot.tableTokens.forEach { (tableId, token) ->
            tokenStore?.saveToken("table", tableId, token)
        }
    }

    private suspend fun fetchBootstrap(fallbackMessage: String): BootstrapResponse {
        val response = orderService.getBootstrap()

        if (!response.isSuccessful || response.body()?.isSuccess != true) {
            throw IllegalStateException(response.body()?.message ?: fallbackMessage)
        }

        return response.body()?.data
            ?: error("Brak danych manifestu")
    }

    private suspend fun syncOrdersAndItems() {
        val bootstrap = fetchBootstrap("Blad pobierania manifestu")
        val snapshot = fetchOrdersAndItemsSnapshot(bootstrap)
        applyOrdersAndItemsSnapshot(snapshot)
        persistOrderSnapshotTokens(snapshot)
    }

    private suspend fun fetchOrdersAndItemsSnapshot(bootstrap: BootstrapResponse): OrdersAndItemsSyncSnapshot {
        val totalOrderPages = bootstrap.modules["orders"]?.totalPages ?: 0
        val syncedOrders = mutableListOf<OrderEntity>()
        val orderTokens = mutableListOf<OrderTokenSnapshot>()

        if (totalOrderPages > 0) {
            for (page in 1..totalOrderPages) {
                val response = orderService.syncOrders(page = page)

                if (!response.isSuccessful || response.body()?.isSuccess != true) {
                    throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji zamowien")
                }

                response.body()
                    ?.data
                    ?.items
                    .orEmpty()
                    .forEach { dto ->
                        val order = dto.toOrderEntity()
                        syncedOrders.add(order)
                        orderTokens.add(
                            OrderTokenSnapshot(
                                orderId = order.id,
                                orderToken = dto.token,
                                reservationToken = dto.reservationToken
                            )
                        )
                    }
            }
        }

        val totalItemPages = bootstrap.modules["orderItems"]?.totalPages ?: 0
        val syncedItems = mutableListOf<OrderItemEntity>()
        val orderItemTokens = mutableListOf<Pair<UUID, String>>()

        if (totalItemPages > 0) {
            for (page in 1..totalItemPages) {
                val response = orderService.syncOrderItems(page = page)

                if (!response.isSuccessful || response.body()?.isSuccess != true) {
                    throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji pozycji")
                }

                response.body()
                    ?.data
                    ?.items
                    .orEmpty()
                    .forEach { dto ->
                        val item = dto.toOrderItemEntity()
                        syncedItems.add(item)
                        orderItemTokens.add(item.id to dto.token)
                    }
            }
        }

        return OrdersAndItemsSyncSnapshot(
            orders = syncedOrders,
            orderTokens = orderTokens,
            orderItems = syncedItems,
            orderItemTokens = orderItemTokens
        )
    }

    private suspend fun applyOrdersAndItemsSnapshot(snapshot: OrdersAndItemsSyncSnapshot) {
        val preservedLocalOrders = localReservationOrdersToPreserve(snapshot)
        val ordersToKeep = snapshot.orders + preservedLocalOrders

        if (ordersToKeep.isEmpty()) {
            database.orderDao().clearOrders()
        } else {
            database.orderDao().insertOrders(ordersToKeep)
            database.orderDao().deleteOrdersExcept(ordersToKeep.map { it.id })
        }

        if (snapshot.orderItems.isEmpty()) {
            database.orderDao().clearOrderItems()
        } else {
            database.orderDao().insertOrderItems(snapshot.orderItems)
            database.orderDao().deleteOrderItemsExcept(snapshot.orderItems.map { it.id })
        }
    }

    private suspend fun localReservationOrdersToPreserve(
        snapshot: OrdersAndItemsSyncSnapshot
    ): List<OrderEntity> {
        val remoteReservationTokens = snapshot.orderTokens
            .mapNotNull { it.reservationToken }
            .toSet()

        return database.orderDao()
            .getAllOrdersOnce()
            .filter { order ->
                val reservationToken = tokenStore?.getOrderReservationToken(order.id)
                val hasRemoteOrderForReservation = reservationToken != null &&
                    reservationToken in remoteReservationTokens
                val isLocalOnlyOrder = tokenStore?.getToken("order", order.id).isNullOrBlank()
                val reservation = reservationToken
                    ?.let { token -> database.reservationDao().getReservationByIdOnce(token.toStableUUID()) }

                isLocalOnlyOrder &&
                    !hasRemoteOrderForReservation &&
                    order.isActiveForTable() &&
                    order.waiterId != null &&
                    reservation?.isInProgressForTable() == true
            }
    }

    private suspend fun clearWaitersForReleasedTables() {
        val releasedStatuses = setOf("AVAILABLE", "CLEANING", "OUT_OF_SERVICE")
        val now = getCurrentTimestamp()
        database.restaurantTableDao()
            .getAllTablesOnce()
            .forEach { table ->
                val statusToken = table.statusId
                    ?.let { statusId -> database.tableStatusDao().getStatusByIdOnce(statusId)?.token }
                    ?.uppercase(Locale.US)

                if (statusToken in releasedStatuses) {
                    database.orderDao().clearOrderWaitersForTable(table.id, now)
                    clearReservationWaitersForTable(table.id)
                    tokenStore?.clearLocalTableStatusOverride(table.id)
                }
            }
    }

    private suspend fun clearWaitersForTable(tableId: UUID) {
        database.orderDao().clearOrderWaitersForTable(tableId, getCurrentTimestamp())
        clearReservationWaitersForTable(tableId)
    }

    private suspend fun clearReservationWaitersForTable(tableId: UUID) {
        database.reservationDao()
            .getAllReservationsOnce()
            .filter { reservation -> reservation.tableId == tableId }
            .forEach { reservation -> tokenStore?.clearReservationWaiterId(reservation.token) }
    }

    private fun persistOrderSnapshotTokens(snapshot: OrdersAndItemsSyncSnapshot) {
        snapshot.orderTokens.forEach { token ->
            tokenStore?.saveToken("order", token.orderId, token.orderToken)
            tokenStore?.saveOrderReservationToken(token.orderId, token.reservationToken)
        }
        snapshot.orderItemTokens.forEach { (orderItemId, token) ->
            tokenStore?.saveToken("orderItem", orderItemId, token)
        }
    }

    private suspend fun syncUsers(preservedPasswords: Map<String, String> = emptyMap()) {
        val effectivePreservedPasswords = preservedPasswords.ifEmpty {
            database.userDao()
                .getAllUsersOnce()
                .filter { it.password.isNotBlank() }
                .toPreservedPasswordMap()
        }
        val bootstrapResponse = orderService.getBootstrap()

        if (!bootstrapResponse.isSuccessful || bootstrapResponse.body()?.isSuccess != true) {
            throw IllegalStateException(bootstrapResponse.body()?.message ?: "Blad pobierania manifestu uzytkownikow")
        }

        val totalPages = bootstrapResponse.body()
            ?.data
            ?.modules
            ?.get("users")
            ?.totalPages
            ?: 0

        if (totalPages <= 0) return

        for (page in 1..totalPages) {
            val response = orderService.syncUsers(page = page)

            if (!response.isSuccessful || response.body()?.isSuccess != true) {
                throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji uzytkownikow")
            }

            val usersDto = response.body()
                ?.data
                ?.items
                .orEmpty()

            val users = mutableListOf<UsersEntity>()
            for (dto in usersDto) {
                val user = dto.toUsersEntity(effectivePreservedPasswords)
                tokenStore?.saveToken("user", user.id, dto.token)
                users.add(user)
            }

            database.userDao().insertUsers(users)
        }
    }

    private suspend fun fetchUsersSnapshot(
        bootstrap: BootstrapResponse,
        preservedPasswords: Map<String, String> = emptyMap()
    ): UsersSyncSnapshot {
        val effectivePreservedPasswords = preservedPasswords.ifEmpty {
            database.userDao()
                .getAllUsersOnce()
                .filter { it.password.isNotBlank() }
                .toPreservedPasswordMap()
        }
        val totalPages = bootstrap.modules["users"]?.totalPages ?: 0

        if (totalPages <= 0) {
            return UsersSyncSnapshot()
        }

        val users = mutableListOf<UsersEntity>()
        val userTokens = mutableListOf<Pair<UUID, String>>()

        for (page in 1..totalPages) {
            val response = orderService.syncUsers(page = page)

            if (!response.isSuccessful || response.body()?.isSuccess != true) {
                throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji uzytkownikow")
            }

            response.body()
                ?.data
                ?.items
                .orEmpty()
                .forEach { dto ->
                    val user = dto.toUsersEntity(effectivePreservedPasswords)
                    users.add(user)
                    userTokens.add(user.id to dto.token)
                }
        }

        return UsersSyncSnapshot(users = users, userTokens = userTokens)
    }

    private suspend fun applyUsersSnapshot(snapshot: UsersSyncSnapshot) {
        if (snapshot.users.isNotEmpty()) {
            database.userDao().insertUsers(snapshot.users)
        }
    }

    private fun persistUsersSnapshotTokens(snapshot: UsersSyncSnapshot) {
        snapshot.userTokens.forEach { (userId, token) ->
            tokenStore?.saveToken("user", userId, token)
        }
    }

    private suspend fun syncReservations() {
        var page = 1
        val nowMillis = System.currentTimeMillis()
        val currentReservationByTable = mutableMapOf<UUID, ReservationEntity>()
        val syncedReservations = mutableListOf<ReservationEntity>()

        while (true) {
            val response = orderService.syncReservations(page = page)

            if (!response.isSuccessful || response.body()?.isSuccess != true) {
                throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji rezerwacji")
            }

            val reservations = response.body()?.data?.items.orEmpty()
            if (reservations.isEmpty()) break

            reservations.forEach { reservation ->
                storeReservationTokens(reservation)
                val entity = reservation.toReservationEntity()
                syncedReservations.add(entity)

                if (ReservationTimeUtils.isCurrent(entity, nowMillis)) {
                    val current = currentReservationByTable[entity.tableId]

                    if (current == null || entity.updatedAt >= current.updatedAt) {
                        currentReservationByTable[entity.tableId] = entity
                    }
                }
            }

            page++
        }

        database.withTransaction {
            if (syncedReservations.isEmpty()) {
                database.reservationDao().clearAll()
            } else {
                database.reservationDao().insertAll(syncedReservations)
                database.reservationDao().deleteReservationsExcept(syncedReservations.map { it.id })
            }
        }

        currentReservationByTable.forEach { (tableId, reservation) ->
            tokenStore?.saveTableReservationToken(tableId, reservation.token)
        }

        database.restaurantTableDao()
            .getAllTablesOnce()
            .filter { it.id !in currentReservationByTable.keys }
            .forEach { table -> tokenStore?.clearTableReservationToken(table.id) }
    }

    private suspend fun fetchReservationsSnapshot(): ReservationsSyncSnapshot {
        var page = 1
        val nowMillis = System.currentTimeMillis()
        val currentReservationByTable = mutableMapOf<UUID, ReservationEntity>()
        val syncedReservations = mutableListOf<ReservationEntity>()
        val reservationTokens = mutableListOf<ReservationTokenSnapshot>()

        while (true) {
            val response = orderService.syncReservations(page = page)

            if (!response.isSuccessful || response.body()?.isSuccess != true) {
                throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji rezerwacji")
            }

            val reservations = response.body()?.data?.items.orEmpty()
            if (reservations.isEmpty()) break

            reservations.forEach { reservation ->
                val entity = reservation.toReservationEntity()
                syncedReservations.add(entity)
                reservationTokens.add(
                    ReservationTokenSnapshot(
                        reservationId = entity.id,
                        reservationToken = reservation.token,
                        userToken = reservation.userToken
                    )
                )

                if (ReservationTimeUtils.isCurrent(entity, nowMillis)) {
                    val current = currentReservationByTable[entity.tableId]

                    if (current == null || entity.updatedAt >= current.updatedAt) {
                        currentReservationByTable[entity.tableId] = entity
                    }
                }
            }

            page++
        }

        return ReservationsSyncSnapshot(
            reservations = syncedReservations,
            reservationTokens = reservationTokens,
            currentReservationTokensByTableId = currentReservationByTable
                .mapValues { (_, reservation) -> reservation.token }
        )
    }

    private suspend fun applyReservationsSnapshot(snapshot: ReservationsSyncSnapshot) {
        if (snapshot.reservations.isEmpty()) {
            database.reservationDao().clearAll()
        } else {
            database.reservationDao().insertAll(snapshot.reservations)
            database.reservationDao().deleteReservationsExcept(snapshot.reservations.map { it.id })
        }
    }

    private suspend fun persistReservationSnapshotTokens(
        snapshot: ReservationsSyncSnapshot,
        knownTableIds: List<UUID>
    ) {
        snapshot.reservationTokens.forEach { token ->
            tokenStore?.saveToken("reservation", token.reservationId, token.reservationToken)
            tokenStore?.saveReservationUserToken(token.reservationToken, token.userToken)
        }

        snapshot.currentReservationTokensByTableId.forEach { (tableId, reservationToken) ->
            tokenStore?.saveTableReservationToken(tableId, reservationToken)
        }

        val tableIds = knownTableIds.ifEmpty {
            database.restaurantTableDao().getAllTablesOnce().map { it.id }
        }
        tableIds
            .filter { tableId -> tableId !in snapshot.currentReservationTokensByTableId.keys }
            .forEach { tableId -> tokenStore?.clearTableReservationToken(tableId) }
    }

    private fun storeReservationTokens(reservation: ReservationSyncDto) {
        val reservationId = reservation.token.toStableUUID()

        tokenStore?.saveToken("reservation", reservationId, reservation.token)
        tokenStore?.saveReservationUserToken(reservation.token, reservation.userToken)
    }

    private val menuSyncHandler by lazy {
        MenuSyncHandler(
            database = database,
            dishService = dishService,
            tokenStore = tokenStore,
            cacheDishImage = dishImageCache::cacheDishImage
        )
    }

    suspend fun syncMenu(): Result<Unit> = menuSyncHandler.syncMenu()
    suspend fun changeTableStatusRemote(
        tableId: UUID,
        statusToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            changeTableStatus(tableId, statusToken)
        }
    }

    private suspend fun changeTableStatus(
        tableId: UUID,
        statusToken: String
    ) {
        val normalizedStatusToken = statusToken.uppercase(Locale.US)
        val tableToken = getRemoteToken("table", tableId)
        val completionReservationToken = if (normalizedStatusToken == "CLEANING") {
            findReservationTokenForCompletingTable(tableId)
        } else {
            null
        }
        val (path, optimisticAction) = tableStatusPathAndAction(
            tableToken = tableToken,
            statusToken = normalizedStatusToken,
            completionReservationToken = completionReservationToken
        )

        val remoteResult = sendTableStatusChangeToApi(
            tableToken = tableToken,
            statusToken = normalizedStatusToken,
            completionReservationToken = completionReservationToken
        )
        if (remoteResult.isSuccess) {
            syncOperationalData("table-status-$normalizedStatusToken")
                .onFailure { error ->
                    Log.w(
                        "TABLE_REMOTE",
                        "Status stolika zapisany w API, ale synchronizacja po zapisie nie powiodla sie: ${error.message}"
                    )
                }
            applyLocalTableStatus(tableId, normalizedStatusToken)
            clearWaitersForTable(tableId)
            return
        }

        val error = remoteResult.exceptionOrNull()
        if (error !is IOException) {
            syncOperationalData("table-status-rejected")
                .onFailure { syncError ->
                    Log.w("TABLE_REMOTE", "Synchronizacja po odrzuceniu statusu stolika nie powiodla sie: ${syncError.message}")
                }
            if (shouldKeepLocalAvailableAfterRemoteRejection(normalizedStatusToken, error)) {
                clearWaitersForTable(tableId)
                applyLocalTableStatus(tableId, "AVAILABLE")
                return
            }
            throw error ?: IllegalStateException("Nie udalo sie zapisac statusu stolika w API")
        }

        applyLocalTableStatus(tableId, normalizedStatusToken)
        clearWaitersForTable(tableId)
        enqueueTableStatusChange(tableId, path, optimisticAction)
    }

    private suspend fun shouldKeepLocalCleaningAfterRemoteRejection(
        tableId: UUID,
        statusToken: String,
        error: Throwable?
    ): Boolean {
        if (statusToken != "CLEANING") return false
        if (hasLocallyOccupiedOrder(tableId)) return true

        val message = error
            ?.message
            ?.normalizedApiErrorMessage()
            ?: return false

        return message.contains("wolny stolik nie wymaga sprzatania") ||
            message.contains("wolny stolik nie wymaga sprzątania") ||
            message.contains("available table does not require cleaning")
    }

    private fun shouldTreatCleaningRejectedAsAlreadyReleased(
        statusToken: String,
        error: Throwable?
    ): Boolean {
        if (statusToken != "CLEANING") return false

        val message = error
            ?.message
            ?.normalizedApiErrorMessage()
            ?: return false

        return message.contains("wolny stolik nie wymaga sprzatania") ||
            message.contains("available table does not require cleaning")
    }

    private fun shouldKeepLocalAvailableAfterRemoteRejection(
        statusToken: String,
        error: Throwable?
    ): Boolean {
        if (statusToken != "AVAILABLE") return false

        val message = error
            ?.message
            ?.normalizedApiErrorMessage()
            ?: return false

        return message.contains("stolik juz jest wolny") ||
            message.contains("stolik już jest wolny") ||
            message.contains("table is already available") ||
            message.contains("table already available") ||
            message.contains("already available") ||
            message.contains("wolny stolik nie wymaga sprzatania") ||
            message.contains("available table does not require cleaning")
    }

    private fun String.normalizedApiErrorMessage(): String {
        return lowercase(Locale.US)
            .replace('\u0105', 'a')
            .replace('\u0107', 'c')
            .replace('\u0119', 'e')
            .replace('\u0142', 'l')
            .replace('\u0144', 'n')
            .replace('\u00f3', 'o')
            .replace('\u015b', 's')
            .replace('\u017a', 'z')
            .replace('\u017c', 'z')
    }

    private suspend fun hasLocallyOccupiedOrder(tableId: UUID): Boolean {
        return database.orderDao()
            .getAllOrdersOnce()
            .any { order -> order.tableId == tableId && order.isOccupiedForTable() }
    }

    private suspend fun findReservationTokenForCompletingTable(tableId: UUID): String? {
        val nowMillis = System.currentTimeMillis()
        val tableReservations = database.reservationDao()
            .getAllReservationsOnce()
            .filter { reservation -> reservation.tableId == tableId }

        val selectedReservation = selectInProgressReservation(tableReservations, nowMillis)
            ?: ReservationTimeUtils.selectCurrentOrUpcoming(tableReservations, nowMillis)
            ?: return null

        if (selectedReservation.isInProgressForTable()) {
            return selectedReservation.token
        }

        return database.orderDao()
            .getAllOrdersOnce()
            .filter { order -> order.tableId == tableId && order.isOccupiedForTable() }
            .firstNotNullOfOrNull { order ->
                tokenStore
                    ?.getOrderReservationToken(order.id)
                    ?.takeIf { reservationToken -> reservationToken == selectedReservation.token }
            }
    }

    private fun selectInProgressReservation(
        reservations: List<ReservationEntity>,
        nowMillis: Long
    ): ReservationEntity? {
        val inProgressReservations = reservations
            .filter { reservation -> reservation.isActive && reservation.isInProgressForTable() }

        return inProgressReservations
            .filter { reservation ->
                reservation.startEpochMillis <= nowMillis &&
                    reservation.endEpochMillis > nowMillis
            }
            .minByOrNull { reservation -> reservation.startEpochMillis }
            ?: inProgressReservations
                .filter { reservation -> reservation.startEpochMillis <= nowMillis }
                .maxByOrNull { reservation -> reservation.startEpochMillis }
            ?: inProgressReservations.minByOrNull { reservation -> reservation.startEpochMillis }
    }

    private suspend fun sendTableStatusChangeToApi(
        tableToken: String,
        statusToken: String,
        completionReservationToken: String?
    ): Result<Unit> = runCatching {
        val response = when (statusToken) {
            "AVAILABLE" -> tableService.markTableAvailable(tableToken)
            "CLEANING" -> completionReservationToken
                ?.let { reservationToken -> orderService.completeReservation(reservationToken) }
                ?: tableService.markTableCleaning(tableToken)
            "OUT_OF_SERVICE" -> tableService.markTableOutOfService(tableToken)
            else -> throw UnsupportedOperationException(
                "API nie udostepnia bezposredniej zmiany statusu stolika na $statusToken"
            )
        }

        response.requireApiSuccess("Nie udalo sie zapisac statusu stolika w API")
    }

    private fun tableStatusPathAndAction(
        tableToken: String,
        statusToken: String,
        completionReservationToken: String?
    ): Pair<String, String> {
        return when (statusToken) {
            "AVAILABLE" -> "tables/$tableToken/avalaible" to
                PendingRequestRepository.ACTION_MARK_AVAILABLE
            "CLEANING" -> (
                completionReservationToken
                    ?.let { reservationToken -> "reservations/$reservationToken/complete" }
                    ?: "tables/$tableToken/clear"
                ) to
                PendingRequestRepository.ACTION_MARK_CLEANING
            "OUT_OF_SERVICE" -> "tables/$tableToken/out-of-services" to
                PendingRequestRepository.ACTION_MARK_OUT_OF_SERVICE
            else -> throw UnsupportedOperationException(
                "API nie udostepnia bezposredniej zmiany statusu stolika na $statusToken"
            )
        }
    }

    private suspend fun enqueueTableStatusChange(
        tableId: UUID,
        path: String,
        optimisticAction: String
    ) {
        val pendingRepository = pendingRequestRepository
            ?: error("Kolejka requestow wymaga kontekstu aplikacji")

        pendingRepository.enqueue(
            method = PendingRequestRepository.METHOD_PATCH,
            path = path,
            entityType = PendingRequestRepository.ENTITY_TABLE,
            entityLocalId = tableId.toString(),
            optimisticAction = optimisticAction
        )
    }


    suspend fun prepareOrderForOccupyingTable(tableId: UUID): Result<UUID> = withContext(Dispatchers.IO) {
        runCatching {
            syncOperationalData("prepare-occupy-order")
                .onFailure { error ->
                    Log.w(
                        "OCCUPY_TABLE",
                        "Nie udalo sie odswiezyc danych przed zajeciem stolika, uzywam lokalnej kopii: ${error.message}"
                    )
                }

            val reservationSelection = selectReservationForOccupyingTable(tableId)
                ?: throw UnsupportedOperationException(
                    "Nie mozna zajac stolika bez aktualnej lub nadchodzacej rezerwacji."
                )

            findOrderIdForReservation(tableId, reservationSelection.token)
                ?: createLocalOrderForReservation(tableId, reservationSelection.token)
        }
    }

    suspend fun assignWaiterToReservationForOrder(orderId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reservationToken = getReservationTokenForOrder(orderId)
            val tableId = tableIdForOrderOrReservation(orderId)

            orderService.assignWaiterToReservation(reservationToken)
                .requireApiSuccess("Nie udalo sie przypisac kelnera w API")

            syncOperationalData("assign-waiter-on-order-save")
                .onFailure { error ->
                    Log.w(
                        "ORDER_REMOTE",
                        "Przypisano kelnera w API, ale synchronizacja po zapisie nie powiodla sie: ${error.message}"
                    )
                }
            tableId?.let { applyLocalTableStatus(it, "OCCUPIED") }
            Unit
        }
    }

    suspend fun refreshAssignmentStateForOrder(
        orderId: UUID,
        reason: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            syncOperationalData(reason)
                .onFailure { error ->
                    Log.w("ORDER_REMOTE", "Nie udalo sie odswiezyc stanu przypisania kelnera: ${error.message}")
                }
            isReservationAssignmentAppliedForOrder(orderId)
        }
    }

    suspend fun isReservationAssignmentAppliedForOrder(orderId: UUID): Boolean = withContext(Dispatchers.IO) {
        val order = database.orderDao()
            .getAllOrdersOnce()
            .firstOrNull { it.id == orderId }
        val reservation = reservationForOrder(orderId)

        ReservationAssignmentLogic.isAssignmentApplied(order, reservation)
    }

    suspend fun rememberReservationWaiterForOrder(
        orderId: UUID,
        waiterId: UUID
    ) = withContext(Dispatchers.IO) {
        tokenStore
            ?.getOrderReservationToken(orderId)
            ?.let { reservationToken -> tokenStore.saveReservationWaiterId(reservationToken, waiterId) }
    }

    private suspend fun reservationForOrder(orderId: UUID): ReservationEntity? {
        val reservationToken = tokenStore?.getOrderReservationToken(orderId) ?: return null
        return database.reservationDao().getReservationByIdOnce(reservationToken.toStableUUID())
    }

    private suspend fun tableIdForOrderOrReservation(orderId: UUID): UUID? {
        database.orderDao()
            .getAllOrdersOnce()
            .firstOrNull { it.id == orderId }
            ?.tableId
            ?.let { return it }

        return reservationForOrder(orderId)?.tableId
    }

    private suspend fun findOrderIdForReservation(
        tableId: UUID,
        reservationToken: String
    ): UUID? {
        val tableOrders = database.orderDao()
            .getAllOrdersOnce()
            .filter { it.tableId == tableId }

        val reservationOrderId = tableOrders
            .firstOrNull { order ->
                tokenStore?.getOrderReservationToken(order.id) == reservationToken &&
                    order.isActiveForTable()
            }
            ?.id

        if (tokenStore != null) return reservationOrderId

        return reservationOrderId
            ?: tableOrders.firstOrNull { it.waiterId != null && it.isActiveForTable() }?.id
            ?: database.orderDao().getActiveOrderForTableOnce(tableId)
                ?.takeIf { it.isActiveForTable() }
                ?.id
    }

    private suspend fun createLocalOrderForReservation(
        tableId: UUID,
        reservationToken: String
    ): UUID {
        val orderId = "local-order-$reservationToken".toStableUUID()
        val existingOrder = database.orderDao()
            .getAllOrdersOnce()
            .firstOrNull { it.id == orderId }

        if (existingOrder == null) {
            val now = getCurrentTimestamp()
            database.orderDao().insertOrder(
                OrderEntity(
                    id = orderId,
                    tableId = tableId,
                    waiterId = null,
                    statusId = null,
                    statusTokens = "PENDING",
                    totalPrice = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        tokenStore?.saveOrderReservationToken(orderId, reservationToken)
        return orderId
    }

    suspend fun saveReservationItemDeltas(
        orderId: UUID,
        additions: Map<UUID, Int>,
        removals: Map<UUID, Int>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reservationToken = getReservationTokenForOrder(orderId)
            val affectedDishIds = additions.keys + removals.keys

            if (affectedDishIds.any { dishId -> tokenStore?.getToken("dish", dishId).isNullOrBlank() }) {
                syncMenu().getOrThrow()
            }

            val addPayload = additions
                .filterValues { it > 0 }
                .map { (dishId, quantity) ->
                    ReservationDishRequest(
                        dishToken = getRemoteToken("dish", dishId),
                        quantity = quantity
                    )
                }

            if (addPayload.isNotEmpty()) {
                orderService.addReservationItems(reservationToken, addPayload)
                    .requireApiSuccess("Nie udalo sie dodac pozycji zamowienia w API")
            }

            removals
                .filterValues { it > 0 }
                .forEach { (dishId, quantity) ->
                    orderService.removeReservationItem(
                        reservationToken = reservationToken,
                        item = ReservationDishRequest(
                            dishToken = getRemoteToken("dish", dishId),
                            quantity = quantity
                        )
                    ).requireApiSuccess("Nie udalo sie usunac pozycji zamowienia w API")
                }

            syncOperationalData("reservation-item-deltas")
                .onFailure { error ->
                    Log.w(
                        "ORDER_REMOTE",
                        "Pozycje zamowienia zapisane w API, ale synchronizacja po zapisie nie powiodla sie: ${error.message}"
                    )
                }
            Unit
        }
    }

    private suspend fun selectReservationForOccupyingTable(tableId: UUID): OccupyReservationSelection? {
        val nowMillis = System.currentTimeMillis()
        val reservationDao = database.reservationDao()

        val current = reservationDao.getCurrentReservationForTable(tableId, nowMillis)
        val upcoming = if (current == null) {
            reservationDao.getUpcomingReservationForTable(tableId, nowMillis)
        } else {
            null
        }

        val selection = ReservationSelectionLogic.chooseReservationForOccupy(
            currentToken = current?.token,
            upcomingToken = upcoming?.token
        )

        if (selection != null) {
            Log.d(
                "OCCUPY_TABLE",
                "Using ${selection.type} reservation for tableId=$tableId"
            )
        } else {
            Log.w(
                "OCCUPY_TABLE",
                "No current or upcoming reservation found for tableId=$tableId"
            )
        }

        return selection
    }

    suspend fun createClientReportForTable(
        tableId: UUID,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            syncOperationalData().getOrThrow()

            val activeOrder = database.orderDao().getActiveOrderForTableOnce(tableId)
                ?: throw IllegalStateException("Brak aktywnego zamowienia dla stolika")

            val reservationToken = getReservationTokenForOrder(activeOrder.id)
            val clientToken = tokenStore?.getReservationUserToken(reservationToken)
                ?: throw IllegalStateException("Brak tokena klienta dla aktywnej rezerwacji")

            orderService.createReport(
                ReportCreateRequest(
                    clientToken = clientToken,
                    reason = reason
                )
            ).requireApiSuccess("Nie udalo sie wyslac raportu klienta do API")
        }
    }

    private suspend fun getReservationTokenForOrder(orderId: UUID): String {
        tokenStore?.getOrderReservationToken(orderId)?.let { return it }

        syncOperationalData().getOrThrow()

        tokenStore?.getOrderReservationToken(orderId)?.let { return it }

        throw IllegalStateException("Brak tokena rezerwacji dla zamowienia")
    }

    private fun getRemoteToken(type: String, localId: UUID): String {
        return tokenStore?.getToken(type, localId)
            ?: throw IllegalStateException("Brak tokena API dla $type $localId")
    }

    private fun <T> Response<com.example.quilacarne.data.remote.dto.response.ApiResponse<T>>.requireApiSuccess(
        fallbackMessage: String
    ) {
        val body = body()
        if (!isSuccessful) {
            val rawError = errorBody()?.string()
            throw IllegalStateException(body?.message ?: ApiResponseLogic.errorMessageFromBody(rawError) ?: fallbackMessage)
        }

        ApiResponseLogic.assertWrapperSuccess(body, fallbackMessage)
    }

    private suspend fun applyLocalTableStatus(
        tableId: UUID,
        statusToken: String
    ) {
        val now = getCurrentTimestamp()
        val statusId = statusToken.toStableUUID()

        database.tableStatusDao().insertAll(
            listOf(
                TableStatusEntity(
                    id = statusId,
                    token = statusToken,
                    namePl = statusToken.toTableStatusName(),
                    nameEn = statusToken.toTableStatusNameEn(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        database.restaurantTableDao().updateStatus(tableId, statusId, now)

        tokenStore?.clearLocalTableStatusOverride(tableId)
    }

    private suspend fun restoreOfflineLoginUsers(users: List<UsersEntity>) {
        val restorableUsers = users.filter { it.password.isNotBlank() }
        if (restorableUsers.isEmpty()) return

        restorableUsers.forEach { preservedUser ->
            val existing = database.userDao().getUserByUsername(preservedUser.username)
            if (existing == null || existing.password.isBlank()) {
                database.userDao().insertUser(
                    preservedUser.copy(
                        isActive = existing?.isActive ?: preservedUser.isActive,
                        role = existing?.role ?: preservedUser.role,
                        updatedAt = getCurrentTimestamp()
                    )
                )
            }
        }
    }

    private suspend fun clearCurrentSessionForMatchingPersonnelEvent(
        topic: String,
        rawMessage: String
    ): Boolean {
        if (topic != PERSONNEL_UPDATES_TOPIC) return false

        val manager = tokenManager ?: return false
        val store = tokenStore ?: return false
        val event = gson.fromJson(rawMessage, WebSocketEvent::class.java) ?: return false
        val eventType = event.eventType.normalizedEventText() ?: return false
        val entityType = event.entityType.normalizedEventText() ?: return false

        if (eventType !in CURRENT_USER_SESSION_CLEAR_EVENT_TYPES || entityType != "EMPLOYEE") {
            return false
        }

        val eventTokens = event.candidateUserTokens()
        if (eventTokens.isEmpty()) return false

        val currentUsername = manager.getCurrentUsername().trimmedOrNull() ?: return false
        val currentUser = database.userDao().getUserByUsername(currentUsername) ?: return false
        val currentUserToken = store.getToken("user", currentUser.id).trimmedOrNull() ?: return false

        if (eventTokens.none { it == currentUserToken }) return false

        manager.clearTokens()
        Log.i("QLC_WS_EVENT", "Wyczyszczono lokalna sesje po evencie personnel $eventType dla zalogowanego usera")
        return true
    }

    private fun WebSocketEvent.candidateUserTokens(): Set<String> {
        return listOfNotNull(token.trimmedOrNull(), payload.payloadToken())
            .toSet()
    }

    private fun JsonElement?.payloadToken(): String? {
        if (this == null || !isJsonObject) return null

        val tokenElement = asJsonObject.get("token")
        if (tokenElement == null || !tokenElement.isJsonPrimitive) return null

        return tokenElement.asString.trimmedOrNull()
    }

    private fun String?.normalizedEventText(): String? {
        return trimmedOrNull()?.uppercase(Locale.US)
    }

    private fun String?.trimmedOrNull(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
