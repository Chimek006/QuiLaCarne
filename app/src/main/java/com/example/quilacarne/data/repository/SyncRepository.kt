package com.example.quilacarne.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.quilacarne.BuildConfig
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.GuestReportEntity
import com.example.quilacarne.data.local.entities.GuestReportStatusEntity
import com.example.quilacarne.data.local.entities.IngredientAllergenEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.OrderStatusEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.local.entities.isActiveForTable
import com.example.quilacarne.data.remote.dto.BootstrapResponse
import com.example.quilacarne.data.remote.dto.CategoryDto
import com.example.quilacarne.data.remote.dto.DishSyncDto
import com.example.quilacarne.data.remote.dto.GuestReportSyncDto
import com.example.quilacarne.data.remote.dto.IngredientSyncDto
import com.example.quilacarne.data.remote.dto.OrderItemSyncDto
import com.example.quilacarne.data.remote.dto.OrderSyncDto
import com.example.quilacarne.data.remote.dto.ReportCreateRequest
import com.example.quilacarne.data.remote.dto.ReservationDishRequest
import com.example.quilacarne.data.remote.dto.ReservationSyncDto
import com.example.quilacarne.data.remote.dto.SyncDictionaryDto
import com.example.quilacarne.data.remote.dto.TableDto
import com.example.quilacarne.data.remote.dto.UserSyncDto
import com.example.quilacarne.data.remote.dto.WebSocketEvent
import com.example.quilacarne.data.remote.dto.values
import com.example.quilacarne.data.remote.network.ApiLoggingInterceptor
import com.example.quilacarne.data.remote.network.AuthInterceptor
import com.example.quilacarne.data.remote.network.RemoteTokenStore
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.network.TokenAuthenticator
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
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class SyncRepository(
    private val database: AppDatabase,
    private val appContext: Context? = null
) {
    companion object {
        private val operationalSyncMutex = Mutex()
        private val _isOperationalSyncRunning = MutableStateFlow(false)
        val isOperationalSyncRunning: StateFlow<Boolean> = _isOperationalSyncRunning
    }

    private val dishService = RetrofitClient.dishService
    private val tableService = RetrofitClient.tableService
    private val orderService = RetrofitClient.orderService
    private val tokenStore: RemoteTokenStore? = appContext?.let { RemoteTokenStore(it) }
    private val gson = Gson()

    private val imageClient: OkHttpClient? by lazy {
        appContext?.let { context ->
            val tokenManager = TokenManager(context)
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenManager))
                .addInterceptor(ApiLoggingInterceptor("API_IMAGE_HTTP"))
                .authenticator(TokenAuthenticator(tokenManager))
                .build()
        }
    }

    private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray())

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    fun getTablesFlow(): Flow<List<RestaurantTableEntity>> {
        return database.restaurantTableDao().getAllTablesFlow()
    }

    fun getTableStatusesFlow(): Flow<List<TableStatusEntity>> {
        return database.tableStatusDao().getAllStatuses()
    }

    fun getOrdersFlow(): Flow<List<OrderEntity>> {
        return database.orderDao().getAllOrders()
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

    suspend fun applyWebSocketEvent(
        topic: String,
        rawMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val event = gson.fromJson(rawMessage, WebSocketEvent::class.java)
                ?: throw IllegalArgumentException("Pusta wiadomosc WebSocket")
            applyWebSocketEvent(topic, event)
        }.onFailure { error ->
            Log.w("QLC_WS_EVENT", "Nie udalo sie obsluzyc eventu topic=$topic message=${error.message}")
        }
    }

    private suspend fun applyWebSocketEvent(
        topic: String,
        event: WebSocketEvent
    ) {
        val eventType = event.eventType.requiredWebSocketText("eventType").uppercase(Locale.US)
        val entityType = event.entityType.requiredWebSocketText("entityType").uppercase(Locale.US)
        val token = event.token.requiredWebSocketText("token")

        Log.d("QLC_WS_EVENT", "Event topic=$topic type=$eventType entity=$entityType token=$token")

        when (eventType) {
            "CREATED", "UPDATED" -> {
                val payload = event.payload.requirePayload(eventType, entityType, token)
                applyWebSocketUpsert(topic, entityType, payload)
            }
            "DELETED" -> applyWebSocketDelete(topic, entityType, token, event.timestamp)
            else -> throw IllegalArgumentException("Nieznany eventType=$eventType")
        }
    }

    private suspend fun applyWebSocketUpsert(
        topic: String,
        entityType: String,
        payload: JsonElement
    ) {
        when (entityType) {
            "TABLE" -> upsertTableFromWebSocket(payload.toDto(TableDto::class.java))
            "RESERVATION" -> upsertReservationFromWebSocket(payload.toDto(ReservationSyncDto::class.java))
            "ORDER" -> upsertOrderFromWebSocket(payload.toDto(OrderSyncDto::class.java))
            "ORDER_ITEM" -> upsertOrderItemFromWebSocket(payload.toDto(OrderItemSyncDto::class.java))
            "DISH" -> upsertDishFromWebSocket(payload.toDto(DishSyncDto::class.java))
            "INGREDIENT" -> upsertIngredientFromWebSocket(payload.toDto(IngredientSyncDto::class.java))
            "ALLERGEN" -> upsertDictionaryFromWebSocket(entityType, payload.toDto(SyncDictionaryDto::class.java))
            "DISH_CATEGORY" -> upsertDictionaryFromWebSocket(entityType, payload.toDto(SyncDictionaryDto::class.java))
            "TABLE_STATUS" -> upsertDictionaryFromWebSocket(entityType, payload.toDto(SyncDictionaryDto::class.java))
            "ORDER_STATUS" -> upsertDictionaryFromWebSocket(entityType, payload.toDto(SyncDictionaryDto::class.java))
            "ORDER_ITEM_STATUS" -> Log.d("QLC_WS_EVENT", "Order item status dictionary has no local table topic=$topic")
            "EMPLOYEE" -> upsertUserFromWebSocket(payload.toDto(UserSyncDto::class.java))
            "REPORT" -> upsertReportFromWebSocket(payload.toDto(GuestReportSyncDto::class.java))
            "BAN" -> Log.d("QLC_WS_EVENT", "Ban event accepted without local persistence topic=$topic")
            else -> throw IllegalArgumentException("Nieznany entityType=$entityType topic=$topic")
        }
    }

    private suspend fun applyWebSocketDelete(
        topic: String,
        entityType: String,
        token: String,
        timestamp: String?
    ) {
        val deletedAt = timestamp?.takeIf { it.isNotBlank() } ?: getCurrentTimestamp()
        val id = token.toStableUUID()

        database.withTransaction {
            when (entityType) {
                "TABLE" -> database.restaurantTableDao().deleteTableById(id)
                "RESERVATION" -> deleteReservationFromWebSocket(id)
                "ORDER" -> database.orderDao().deleteOrderById(id)
                "ORDER_ITEM" -> database.orderDao().deleteOrderItem(id)
                "DISH" -> database.dishDao().markDishDeleted(id, deletedAt)
                "INGREDIENT" -> {
                    database.ingredientDao().markIngredientDeleted(id, deletedAt)
                    database.dishDao().markDishesUnavailableForIngredient(id, deletedAt)
                }
                "ALLERGEN" -> database.ingredientDao().markAllergenDeleted(id, deletedAt)
                "DISH_CATEGORY" -> database.dishCategoryDao().markCategoryDeleted(id, deletedAt)
                "TABLE_STATUS" -> database.tableStatusDao().markStatusDeleted(id, deletedAt)
                "ORDER_STATUS" -> database.orderStatusDao().markStatusDeleted(id, deletedAt)
                "ORDER_ITEM_STATUS" -> Log.d("QLC_WS_EVENT", "Order item status delete has no local table topic=$topic")
                "EMPLOYEE" -> database.userDao().markUserDeleted(id, deletedAt)
                "REPORT" -> database.guestReportDao().markReportDeleted(id, deletedAt)
                "BAN" -> Log.d("QLC_WS_EVENT", "Ban delete accepted without local persistence topic=$topic")
                else -> throw IllegalArgumentException("Nieznany entityType=$entityType topic=$topic")
            }
        }
    }

    private suspend fun upsertTableFromWebSocket(dto: TableDto) {
        val now = getCurrentTimestamp()
        val tableId = dto.token.toStableUUID()
        val existing = database.restaurantTableDao().getTableByIdOnce(tableId)
        val existingStatusToken = existing?.statusId?.let { statusId ->
            database.tableStatusDao().getStatusByIdOnce(statusId)?.token
        }
        val statusToken = TableStatusSyncLogic.resolveSyncedTableStatusToken(
            remoteStatusToken = dto.currentStatusToken(),
            existingStatusToken = existingStatusToken,
            remoteUpdatedAt = dto.updatedAt,
            existingUpdatedAt = existing?.updatedAt
        )
        val table = RestaurantTableEntity(
            id = tableId,
            tableNumber = dto.tableNumber,
            capacity = dto.capacity,
            statusId = statusToken?.toStableUUID(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = dto.updatedAt.ifBlank { now }
        )

        database.withTransaction {
            statusToken?.let { ensureTableStatus(it, now) }
            database.restaurantTableDao().insertTables(listOf(table))
        }
        tokenStore?.saveToken("table", tableId, dto.token)
    }

    private suspend fun upsertReservationFromWebSocket(dto: ReservationSyncDto) {
        val entity = dto.toReservationEntity()
        database.reservationDao().insertAll(listOf(entity))
        storeReservationTokens(dto)
        if (ReservationTimeUtils.isCurrent(entity, System.currentTimeMillis())) {
            tokenStore?.saveTableReservationToken(entity.tableId, entity.token)
        }
    }

    private suspend fun upsertOrderFromWebSocket(dto: OrderSyncDto) {
        val entity = dto.toOrderEntity()
        database.orderDao().insertOrder(entity)
        tokenStore?.saveToken("order", entity.id, dto.token)
        tokenStore?.saveOrderReservationToken(entity.id, dto.reservationToken)
    }

    private suspend fun upsertOrderItemFromWebSocket(dto: OrderItemSyncDto) {
        val entity = dto.toOrderItemEntity()
        database.orderDao().insertOrderItem(entity)
        tokenStore?.saveToken("orderItem", entity.id, dto.token)
    }

    private suspend fun upsertDishFromWebSocket(dto: DishSyncDto) {
        val now = getCurrentTimestamp()
        val dishId = dto.token.toStableUUID()
        val categoryId = dto.categoryToken
            ?.takeIf { it.isNotBlank() }
            ?.toStableUUID()
        val ingredientIds = dto.ingredientTokens.orEmpty().map { it.toStableUUID() }
        val missingIngredients = missingIngredientFallbacks(ingredientIds, now)
        val dish = DishEntity(
            id = dishId,
            categoryId = categoryId,
            name = dto.name,
            price = dto.price,
            isAvailable = dto.isAvailable,
            imageUrl = cacheDishImage(dishId, dto.imageUrl),
            createdAt = now,
            updatedAt = now
        )
        val compositions = dto.ingredientTokens.orEmpty().map { ingredientToken ->
            DishCompositionEntity(
                dishId = dishId,
                ingredientId = ingredientToken.toStableUUID(),
                createdAt = now,
                updatedAt = now
            )
        }

        database.withTransaction {
            categoryId?.let { ensureDishCategory(dto.categoryToken.orEmpty(), it, now) }
            if (missingIngredients.isNotEmpty()) {
                database.ingredientDao().insertIngredients(missingIngredients)
            }
            database.dishDao().insertDishes(listOf(dish))
            database.dishDao().deleteCompositionsForDish(dishId)
            database.dishDao().insertCompositions(compositions)
        }
        tokenStore?.saveToken("dish", dishId, dto.token)
    }

    private suspend fun upsertIngredientFromWebSocket(dto: IngredientSyncDto) {
        val now = getCurrentTimestamp()
        val ingredientId = dto.token.toStableUUID()
        val allergenIds = dto.allergenTokens.orEmpty().map { it.toStableUUID() }
        val missingAllergens = missingAllergenFallbacks(allergenIds, now)
        val links = dto.allergenTokens.orEmpty().map { allergenToken ->
            IngredientAllergenEntity(
                ingredientId = ingredientId,
                allergenId = allergenToken.toStableUUID(),
                createdAt = now,
                updatedAt = now
            )
        }

        database.withTransaction {
            database.ingredientDao().insertIngredients(
                listOf(
                    IngredientEntity(
                        id = ingredientId,
                        namePl = dto.namePl,
                        nameEn = dto.nameEn,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
            if (missingAllergens.isNotEmpty()) {
                database.ingredientDao().insertAllergens(missingAllergens)
            }
            database.ingredientDao().deleteAllergenLinksForIngredient(ingredientId)
            database.ingredientDao().insertIngredientAllergens(links)
        }
        tokenStore?.saveToken("ingredient", ingredientId, dto.token)
    }

    private suspend fun upsertDictionaryFromWebSocket(
        entityType: String,
        dto: SyncDictionaryDto
    ) {
        val now = getCurrentTimestamp()
        val id = dto.token.toStableUUID()
        when (entityType) {
            "ALLERGEN" -> database.ingredientDao().insertAllergens(
                listOf(AllergenEntity(id, dto.polishName(), dto.englishName(), now, now))
            )
            "DISH_CATEGORY" -> database.dishCategoryDao().insertAll(
                listOf(DishCategoryEntity(id, dto.polishName(), dto.englishName(), now, now))
            )
            "TABLE_STATUS" -> database.tableStatusDao().insertAll(
                listOf(TableStatusEntity(id, dto.token, dto.polishName(), dto.englishName(), now, now))
            )
            "ORDER_STATUS" -> database.orderStatusDao().insertAll(
                listOf(OrderStatusEntity(id, dto.polishName(), dto.englishName(), now, now))
            )
        }
    }

    private suspend fun upsertUserFromWebSocket(dto: UserSyncDto) {
        val preservedPasswords = database.userDao()
            .getAllUsersOnce()
            .filter { it.password.isNotBlank() }
            .toPreservedPasswordMap()
        val user = dto.toUsersEntity(preservedPasswords)
        database.userDao().insertUser(user)
        tokenStore?.saveToken("user", user.id, dto.token)
    }

    private suspend fun upsertReportFromWebSocket(dto: GuestReportSyncDto) {
        val now = getCurrentTimestamp()
        val statusToken = dto.statusTokens.firstOrNull { it.isNotBlank() }
        val statusId = statusToken?.toStableUUID()
        val report = GuestReportEntity(
            id = dto.token.toStableUUID(),
            reporterId = dto.reporterToken?.toStableUUID(),
            statusId = statusId,
            reason = dto.reason,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
        database.withTransaction {
            if (statusToken != null && statusId != null) {
                database.guestReportDao().insertStatuses(
                    listOf(GuestReportStatusEntity(statusId, statusToken, statusToken, now, now))
                )
            }
            database.guestReportDao().insertReports(listOf(report))
        }
    }

    private suspend fun deleteReservationFromWebSocket(reservationId: UUID) {
        val existing = database.reservationDao().getReservationByIdOnce(reservationId)
        database.reservationDao().deleteReservationById(reservationId)
        existing?.let { reservation ->
            tokenStore?.clearTableReservationToken(reservation.tableId)
        }
    }

    private suspend fun ensureTableStatus(token: String, now: String) {
        val statusId = token.toStableUUID()
        if (database.tableStatusDao().getStatusByIdOnce(statusId) != null) return

        database.tableStatusDao().insertAll(
            listOf(
                TableStatusEntity(
                    id = statusId,
                    token = token,
                    namePl = token.toTableStatusName(),
                    nameEn = token.toTableStatusNameEn(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
    }

    private suspend fun ensureDishCategory(
        token: String,
        categoryId: UUID,
        now: String
    ) {
        if (database.dishCategoryDao().getCategoryByIdOnce(categoryId) != null) return

        database.dishCategoryDao().insertAll(
            listOf(DishCategoryEntity(categoryId, token, token, now, now))
        )
    }

    private suspend fun missingIngredientFallbacks(
        ingredientIds: List<UUID>,
        now: String
    ): List<IngredientEntity> {
        if (ingredientIds.isEmpty()) return emptyList()

        val existingIds = database.ingredientDao().getExistingIngredientIds(ingredientIds).toSet()
        return ingredientIds
            .filter { it !in existingIds }
            .map { id -> IngredientEntity(id, id.toString(), id.toString(), now, now) }
    }

    private suspend fun missingAllergenFallbacks(
        allergenIds: List<UUID>,
        now: String
    ): List<AllergenEntity> {
        if (allergenIds.isEmpty()) return emptyList()

        val existingIds = database.ingredientDao().getExistingAllergenIds(allergenIds).toSet()
        return allergenIds
            .filter { it !in existingIds }
            .map { id -> AllergenEntity(id, id.toString(), id.toString(), now, now) }
    }

    private fun JsonElement?.requirePayload(
        eventType: String,
        entityType: String,
        token: String
    ): JsonElement {
        require(this != null && !isJsonNull) {
            "Brak payload dla $eventType $entityType token=$token"
        }
        return this
    }

    private fun <T> JsonElement.toDto(clazz: Class<T>): T {
        return requireNotNull(gson.fromJson(this, clazz)) {
            "Nie udalo sie zmapowac payload na ${clazz.simpleName}"
        }
    }

    private fun String?.requiredWebSocketText(fieldName: String): String {
        require(!isNullOrBlank()) { "Brak $fieldName" }
        return trim()
    }

    suspend fun syncAllLocalData(clearBeforeSync: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val preservedOfflineUsers = database.userDao()
                .getAllUsersOnce()
                .filter { it.password.isNotBlank() }
            val preservedPasswords = preservedOfflineUsers.toPreservedPasswordMap()
            val currentUsername = appContext
                ?.let { TokenManager(it).getCurrentUsername() }
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
        val bootstrap = fetchBootstrap("BÄąâ€šĂ„â€¦d pobierania manifestu danych operacyjnych")
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
                Log.w("SYNC", "Nie udaĹ‚o siÄ™ zsynchronizowaÄ‡ sĹ‚ownika statusĂłw stolikĂłw: ${error.message}")
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
                    Log.e("SYNC", "BĹ‚Ä…d API stolikĂłw: ${response.code()}")
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

                    dao.insertTables(allTablesEntities)
                    dao.deleteTablesExcept(allTablesEntities.map { it.id })
                }
                Log.d("SYNC", "Zapisano ${allTablesEntities.size} stolikĂłw do bazy")
            } else {
                Log.w("SYNC", "UWAGA: Brak stolikĂłw z API!")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "BĹ‚Ä…d stolikĂłw: ", e)
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
        if (snapshot.statuses.isNotEmpty()) {
            database.tableStatusDao().insertAll(snapshot.statuses)
        }

        if (snapshot.tables.isNotEmpty()) {
            database.restaurantTableDao().insertTables(snapshot.tables)
            database.restaurantTableDao().deleteTablesExcept(snapshot.tables.map { it.id })
            Log.d("SYNC", "Zapisano ${snapshot.tables.size} stolikow do bazy")
        } else {
            Log.w("SYNC", "UWAGA: Brak stolikow z API!")
        }
    }

    private fun persistTableSnapshotTokens(snapshot: TablesSyncSnapshot) {
        snapshot.tableTokens.forEach { (tableId, token) ->
            tokenStore?.saveToken("table", tableId, token)
        }
    }

    private fun TableDto.currentStatusToken(): String? {
        statusToken
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return TableStatusSyncLogic.chooseStatusToken(statusTokens)
    }

    private fun String.toTableStatusName(): String {
        return TableStatusSyncLogic.tableStatusNamePl(this)
    }

    private fun String.toTableStatusNameEn(): String {
        return TableStatusSyncLogic.tableStatusNameEn(this)
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
        val bootstrapResponse = orderService.getBootstrap()

        if (!bootstrapResponse.isSuccessful || bootstrapResponse.body()?.isSuccess != true) {
            throw IllegalStateException(bootstrapResponse.body()?.message ?: "Blad pobierania manifestu")
        }

        val bootstrap = bootstrapResponse.body()?.data
            ?: error("Brak danych manifestu")

        val totalOrderPages = bootstrap.modules["orders"]?.totalPages ?: 0
        val syncedOrders = mutableListOf<OrderEntity>()

        if (totalOrderPages > 0) {
            for (page in 1..totalOrderPages) {
                val response = orderService.syncOrders(page = page)

                if (!response.isSuccessful || response.body()?.isSuccess != true) {
                    throw IllegalStateException(response.body()?.message ?: "Blad synchronizacji zamowien")
                }

                val orderDtos = response.body()
                    ?.data
                    ?.items
                    .orEmpty()

                orderDtos
                    .forEach { dto ->
                        val order = dto.toOrderEntity()
                        tokenStore?.saveToken("order", order.id, dto.token)
                        tokenStore?.saveOrderReservationToken(order.id, dto.reservationToken)
                        syncedOrders.add(order)
                    }
            }
        }

        database.withTransaction {
            if (syncedOrders.isEmpty()) {
                database.orderDao().clearOrders()
            } else {
                database.orderDao().insertOrders(syncedOrders)
                database.orderDao().deleteOrdersExcept(syncedOrders.map { it.id })
            }
        }

        val totalItemPages = bootstrap.modules["orderItems"]?.totalPages ?: 0
        val syncedItems = mutableListOf<OrderItemEntity>()

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
                        tokenStore?.saveToken("orderItem", item.id, dto.token)
                        syncedItems.add(item)
                    }
            }
        }

        database.withTransaction {
            if (syncedItems.isEmpty()) {
                database.orderDao().clearOrderItems()
            } else {
                database.orderDao().insertOrderItems(syncedItems)
                database.orderDao().deleteOrderItemsExcept(syncedItems.map { it.id })
            }
        }
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
        if (snapshot.orders.isEmpty()) {
            database.orderDao().clearOrders()
        } else {
            database.orderDao().insertOrders(snapshot.orders)
            database.orderDao().deleteOrdersExcept(snapshot.orders.map { it.id })
        }

        if (snapshot.orderItems.isEmpty()) {
            database.orderDao().clearOrderItems()
        } else {
            database.orderDao().insertOrderItems(snapshot.orderItems)
            database.orderDao().deleteOrderItemsExcept(snapshot.orderItems.map { it.id })
        }
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

    private fun ReservationSyncDto.toReservationEntity(): ReservationEntity {
        return ReservationEntity(
            id = token.toStableUUID(),
            token = token,
            tableId = tableToken.toStableUUID(),
            tableToken = tableToken,
            userToken = userToken,
            startTime = startTime,
            endTime = endTime,
            startEpochMillis = ReservationTimeUtils.parseApiTimestampMillis(startTime) ?: 0L,
            endEpochMillis = ReservationTimeUtils.parseApiTimestampMillis(endTime) ?: 0L,
            statusTokens = ReservationTimeUtils.statusTokensToText(statusTokens),
            isActive = ReservationTimeUtils.isActiveStatus(statusTokens),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    suspend fun syncMenu(): Result<Unit> = runCatching {
        val now = getCurrentTimestamp()

        val categoriesPlByToken = dishService.getCategories(lang = "pl")
            .body()
            ?.data
            ?.item
            .orEmpty()
            .associateBy { categoryDto -> categoryDto.token }

        val categoriesEnByToken = dishService.getCategories(lang = "en")
            .body()
            ?.data
            ?.item
            .orEmpty()
            .associateBy { categoryDto -> categoryDto.token }

        val allergensPlByToken = dishService.getAllergens(lang = "pl")
            .body()
            ?.data
            ?.item
            .orEmpty()
            .associate { allergenDto -> allergenDto.token to allergenDto.name }

        val allergensEnByToken = dishService.getAllergens(lang = "en")
            .body()
            ?.data
            ?.item
            .orEmpty()
            .associate { allergenDto -> allergenDto.token to allergenDto.name }

        val categoryEntities = (categoriesPlByToken.keys + categoriesEnByToken.keys)
            .distinct()
            .map { token ->
                val categoryPl = categoriesPlByToken[token]
                val categoryEn = categoriesEnByToken[token]
                DishCategoryEntity(
                    id = token.toStableUUID(),
                    namePl = categoryPl?.name ?: categoryEn?.name ?: token,
                    nameEn = categoryEn?.name ?: categoryPl?.name ?: token,
                    createdAt = now,
                    updatedAt = now
                )
            }

        val allIngredientsDto = mutableListOf<IngredientSyncDto>()
        var page = 1

        while (true) {
            val response = dishService.syncIngredients(page = page)
            if (!response.isSuccessful) break

            val items = response.body()?.data?.items.orEmpty()
            if (items.isEmpty()) break

            allIngredientsDto.addAll(items)
            page++
        }

        val globalIngredientEntities = mutableListOf<IngredientEntity>()
        val allergenEntities = mutableListOf<AllergenEntity>()
        val ingredientAllergenLinks = mutableListOf<IngredientAllergenEntity>()

        allIngredientsDto.forEach { ingDto ->
            val ingredientId = ingDto.token.toStableUUID()

            globalIngredientEntities.add(
                IngredientEntity(
                    id = ingredientId,
                    namePl = ingDto.namePl,
                    nameEn = ingDto.nameEn,
                    createdAt = now,
                    updatedAt = now
                )
            )

            ingDto.allergenTokens.orEmpty().forEach { allergenToken ->
                val allergenId = allergenToken.toStableUUID()

                allergenEntities.add(
                    AllergenEntity(
                        id = allergenId,
                        namePl = allergensPlByToken[allergenToken] ?: allergensEnByToken[allergenToken] ?: allergenToken,
                        nameEn = allergensEnByToken[allergenToken] ?: allergensPlByToken[allergenToken] ?: allergenToken,
                        createdAt = now,
                        updatedAt = now
                    )
                )

                ingredientAllergenLinks.add(
                    IngredientAllergenEntity(
                        ingredientId = ingredientId,
                        allergenId = allergenId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        val allDishesDto = mutableListOf<DishSyncDto>()
        page = 1

        while (true) {
            val response = dishService.syncDishes(page = page)
            if (!response.isSuccessful) break

            val items = response.body()?.data?.items.orEmpty()
            if (items.isEmpty()) break

            allDishesDto.addAll(items)
            page++
        }

        val dishEntities = mutableListOf<DishEntity>()
        val compositionEntities = mutableListOf<DishCompositionEntity>()

        allDishesDto.forEach { dto ->
            val dishId = dto.token.toStableUUID()
            tokenStore?.saveToken("dish", dishId, dto.token)

            val categoryId = dto.categoryToken
                ?.takeIf { it.isNotBlank() }
                ?.toStableUUID()

            dishEntities.add(
                DishEntity(
                    id = dishId,
                    categoryId = categoryId,
                    name = dto.name,
                    price = dto.price,
                    isAvailable = dto.isAvailable,
                    imageUrl = cacheDishImage(dishId, dto.imageUrl),
                    createdAt = now,
                    updatedAt = now
                )
            )

            dto.ingredientTokens.orEmpty().forEach { ingredientToken ->
                compositionEntities.add(
                    DishCompositionEntity(
                        dishId = dishId,
                        ingredientId = ingredientToken.toStableUUID(),
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        database.withTransaction {
            database.dishCategoryDao().insertAll(categoryEntities)
            database.ingredientDao().insertIngredients(globalIngredientEntities)
            database.ingredientDao().insertAllergens(allergenEntities)
            database.ingredientDao().insertIngredientAllergens(ingredientAllergenLinks)
            database.dishDao().insertDishes(dishEntities)
            database.dishDao().insertCompositions(compositionEntities)
        }
    }

    suspend fun changeTableStatusRemote(
        tableId: UUID,
        statusToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (statusToken.uppercase(Locale.US)) {
                "AVAILABLE" -> {
                    tableService.markTableAvailable(getRemoteToken("table", tableId))
                        .requireApiSuccess("Nie udalo sie zapisac statusu stolika w API")
                    syncOperationalData().getOrThrow()
                    applyLocalTableStatus(tableId, statusToken)
                }
                "CLEANING" -> {
                    tableService.markTableCleaning(getRemoteToken("table", tableId))
                        .requireApiSuccess("Nie udalo sie zapisac statusu stolika w API")
                    syncOperationalData().getOrThrow()
                    applyLocalTableStatus(tableId, statusToken)
                }
                "OUT_OF_SERVICE" -> {
                    tableService.markTableOutOfService(getRemoteToken("table", tableId))
                        .requireApiSuccess("Nie udalo sie zapisac statusu stolika w API")
                    syncOperationalData().getOrThrow()
                    applyLocalTableStatus(tableId, statusToken)
                }
                else -> {
                    throw UnsupportedOperationException(
                        "API nie udostepnia bezposredniej zmiany statusu stolika na $statusToken"
                    )
                }
            }
        }
    }


    suspend fun prepareOrderForOccupyingTable(tableId: UUID): Result<UUID> = withContext(Dispatchers.IO) {
        runCatching {
            syncOperationalData("prepare-occupy-order").getOrThrow()

            val reservationSelection = selectReservationForOccupyingTable(tableId)
                ?: throw UnsupportedOperationException(
                    "Nie mozna zajac stolika bez aktualnej lub nadchodzacej rezerwacji."
                )

            findOrderIdForReservation(tableId, reservationSelection.token)
                ?: throw IllegalStateException(
                    "Nie znaleziono zamowienia powiazanego z rezerwacja. " +
                        "Odswiez dane i sprobuj ponownie."
                )
        }
    }

    suspend fun assignWaiterToReservationForOrder(orderId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reservationToken = getReservationTokenForOrder(orderId)

            orderService.assignWaiterToReservation(reservationToken)
                .requireApiSuccess("Nie udalo sie przypisac kelnera w API")

            syncOperationalData("assign-waiter-on-order-save").getOrThrow()
        }
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

            syncOperationalData().getOrThrow()
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

    private fun <T> Response<com.example.quilacarne.data.remote.dto.ApiResponse<T>>.requireApiSuccess(
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
    }

    private suspend fun cacheDishImage(dishId: UUID, imageUrl: String?): String? {
        val remoteUrl = normalizeImageUrl(imageUrl) ?: return null
        val context = appContext ?: return remoteUrl
        val client = imageClient ?: return remoteUrl

        return withContext(Dispatchers.IO) {
            runCatching {
                val extension = getImageExtension(remoteUrl)
                val imageDir = File(context.filesDir, "dish_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }

                val imageFile = File(imageDir, "$dishId.$extension")
                if (imageFile.exists() && imageFile.length() > 0L) {
                    return@runCatching Uri.fromFile(imageFile).toString()
                }

                val request = Request.Builder()
                    .url(remoteUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@runCatching remoteUrl
                    }

                    val stream = response.body?.byteStream() ?: return@runCatching remoteUrl

                    imageFile.outputStream().use { output ->
                        stream.use { input ->
                            input.copyTo(output)
                        }
                    }

                    if (imageFile.length() > 0L) {
                        Uri.fromFile(imageFile).toString()
                    } else {
                        remoteUrl
                    }
                }
            }.getOrElse {
                remoteUrl
            }
        }
    }

    private fun normalizeImageUrl(imageUrl: String?): String? {
        val trimmedUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null

        val resolvedUrl = if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            trimmedUrl
        } else {
            runCatching {
                URI(BuildConfig.BASE_URL).resolve(trimmedUrl.trimStart('/')).toString()
            }.getOrElse {
                trimmedUrl
            }
        }

        return resolvedUrl
            .replace("localhost", URI(BuildConfig.BASE_URL).host)
            .replace("127.0.0.1", URI(BuildConfig.BASE_URL).host)
    }

    private fun getImageExtension(imageUrl: String): String {
        val path = runCatching { URI(imageUrl).path }.getOrNull().orEmpty()
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp") }

        return extension ?: "jpg"
    }

    private fun OrderSyncDto.toOrderEntity(): OrderEntity {
        return OrderEntity(
            id = token.toStableUUID(),
            tableId = tableToken.toStableUUID(),
            waiterId = waiterToken?.toStableUUID(),
            statusId = null,
            statusTokens = statusTokens.joinToString(","),
            totalPrice = totalPrice,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun OrderItemSyncDto.toOrderItemEntity(): OrderItemEntity {
        return OrderItemEntity(
            id = token.toStableUUID(),
            orderId = orderToken.toStableUUID(),
            productId = productToken.toStableUUID(),
            quantity = quantity,
            priceAtTimeOfOrder = priceAtTimeOfOrder,
            statusTokens = statusTokens.joinToString(","),
            note = note,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    } 

    private fun UserSyncDto.toUsersEntity(preservedPasswords: Map<String, String>): UsersEntity {
        val role = roleTokens.orEmpty().joinToString(",").ifBlank { if (isStaff) "ROLE_WAITER" else "ROLE_CLIENT" }
        val preservedPassword = preservedPasswords[username.normalizedLoginKey()]
            ?: email?.normalizedLoginKey()?.let { preservedPasswords[it] }

        return UsersEntity(
            id = token.toStableUUID(),
            username = username,
            password = preservedPassword.orEmpty(),
            isActive = isActive ?: true,
            role = role,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun List<UsersEntity>.toPreservedPasswordMap(): Map<String, String> {
        return flatMap { user ->
            listOf(user.username.normalizedLoginKey() to user.password)
        }.toMap()
    }

    private fun List<UsersEntity>.findByUsername(username: String): UsersEntity? {
        val normalizedUsername = username.normalizedLoginKey()
        return firstOrNull { user -> user.username.normalizedLoginKey() == normalizedUsername }
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

    private fun String.normalizedLoginKey(): String {
        return trim().lowercase(Locale.US)
    }
}

private data class OperationalSyncSnapshot(
    val tables: TablesSyncSnapshot,
    val users: UsersSyncSnapshot,
    val reservations: ReservationsSyncSnapshot,
    val ordersAndItems: OrdersAndItemsSyncSnapshot
)

private data class TablesSyncSnapshot(
    val statuses: List<TableStatusEntity> = emptyList(),
    val tables: List<RestaurantTableEntity> = emptyList(),
    val tableTokens: List<Pair<UUID, String>> = emptyList()
)

private data class UsersSyncSnapshot(
    val users: List<UsersEntity> = emptyList(),
    val userTokens: List<Pair<UUID, String>> = emptyList()
)

private data class ReservationsSyncSnapshot(
    val reservations: List<ReservationEntity> = emptyList(),
    val reservationTokens: List<ReservationTokenSnapshot> = emptyList(),
    val currentReservationTokensByTableId: Map<UUID, String> = emptyMap()
)

private data class ReservationTokenSnapshot(
    val reservationId: UUID,
    val reservationToken: String,
    val userToken: String?
)

private data class OrdersAndItemsSyncSnapshot(
    val orders: List<OrderEntity> = emptyList(),
    val orderTokens: List<OrderTokenSnapshot> = emptyList(),
    val orderItems: List<OrderItemEntity> = emptyList(),
    val orderItemTokens: List<Pair<UUID, String>> = emptyList()
)

private data class OrderTokenSnapshot(
    val orderId: UUID,
    val orderToken: String,
    val reservationToken: String?
)
