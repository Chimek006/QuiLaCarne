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
import com.example.quilacarne.data.local.entities.IngredientAllergenEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.dto.CategoryDto
import com.example.quilacarne.data.remote.dto.DishSyncDto
import com.example.quilacarne.data.remote.dto.IngredientSyncDto
import com.example.quilacarne.data.remote.dto.OrderItemSyncDto
import com.example.quilacarne.data.remote.dto.OrderSyncDto
import com.example.quilacarne.data.remote.dto.ReportCreateRequest
import com.example.quilacarne.data.remote.dto.ReservationDishRequest
import com.example.quilacarne.data.remote.dto.ReservationSyncDto
import com.example.quilacarne.data.remote.dto.TableDto
import com.example.quilacarne.data.remote.dto.UserSyncDto
import com.example.quilacarne.data.remote.dto.values
import com.example.quilacarne.data.remote.network.ApiLoggingInterceptor
import com.example.quilacarne.data.remote.network.AuthInterceptor
import com.example.quilacarne.data.remote.network.RemoteTokenStore
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.network.TokenAuthenticator
import com.example.quilacarne.utils.ReservationTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    }

    private val dishService = RetrofitClient.dishService
    private val tableService = RetrofitClient.tableService
    private val orderService = RetrofitClient.orderService
    private val tokenStore: RemoteTokenStore? = appContext?.let { RemoteTokenStore(it) }

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

    suspend fun syncAllLocalData(clearBeforeSync: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val preservedPasswords = database.userDao()
                .getAllUsersOnce()
                .associate { it.username to it.password }

            if (clearBeforeSync) {
                ensureServerReachable()
                database.clearAllTables()
            }

            syncMenu().getOrThrow()
            syncTables().getOrThrow()
            syncUsers(preservedPasswords)
            syncReservations()
            syncOrdersAndItems()
        }
    }

    suspend fun syncOperationalData(): Result<Unit> = withContext(Dispatchers.IO) {
        operationalSyncMutex.withLock {
            runCatching {
                syncTables().getOrThrow()
                syncUsers()
                syncReservations()
                syncOrdersAndItems()
            }
        }
    }

    private suspend fun ensureServerReachable() {
        try {
            Log.d("SYNC", "Checking csrf...")

            val response = RetrofitClient.authService.csrf()

            Log.d("SYNC", "Code: ${response.code()}")
            Log.d("SYNC", "Body: ${response.body()}")
            Log.d("SYNC", "Error: ${response.errorBody()?.string()}")

            if (!response.isSuccessful) {
                throw Exception("Serwer API jest niedostępny")
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
                    val statusToken = TableStatusSyncLogic.resolveSyncedTableStatusToken(
                        remoteStatusToken = remoteStatusToken,
                        existingStatusToken = existingStatusToken,
                        remoteUpdatedAt = dto.updatedAt,
                        existingUpdatedAt = existingTablesById[tableId]?.updatedAt
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
            throw Exception(responsePl.body()?.message ?: "Blad pobierania statusow")
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

    private fun TableDto.currentStatusToken(): String? {
        return TableStatusSyncLogic.chooseStatusToken(
            buildList {
            statusToken?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(statusTokens.filter { it.isNotBlank() })
            }
        )
    }

    private fun String.toTableStatusName(): String {
        return TableStatusSyncLogic.tableStatusNamePl(this)
    }

    private fun String.toTableStatusNameEn(): String {
        return TableStatusSyncLogic.tableStatusNameEn(this)
    }

    private suspend fun syncOrdersAndItems() {
        val bootstrapResponse = orderService.getBootstrap()

        if (!bootstrapResponse.isSuccessful || bootstrapResponse.body()?.isSuccess != true) {
            throw Exception(bootstrapResponse.body()?.message ?: "Błąd pobierania manifestu")
        }

        val bootstrap = bootstrapResponse.body()?.data
            ?: throw Exception("Brak danych manifestu")

        val totalOrderPages = bootstrap.modules["orders"]?.totalPages ?: 0

        if (totalOrderPages > 0) {
            for (page in 1..totalOrderPages) {
                val response = orderService.syncOrders(page = page)

                if (!response.isSuccessful || response.body()?.isSuccess != true) {
                    throw Exception(response.body()?.message ?: "Błąd synchronizacji zamówień")
                }

                val orderDtos = response.body()
                    ?.data
                    ?.items
                    .orEmpty()

                val orders = orderDtos
                    .map { dto ->
                        val order = dto.toOrderEntity()
                        tokenStore?.saveToken("order", order.id, dto.token)
                        tokenStore?.saveOrderReservationToken(order.id, dto.reservationToken)
                        order
                    }

                database.orderDao().insertOrders(orders)
            }
        }

        val totalItemPages = bootstrap.modules["orderItems"]?.totalPages ?: 0

        if (totalItemPages > 0) {
            for (page in 1..totalItemPages) {
                val response = orderService.syncOrderItems(page = page)

                if (!response.isSuccessful || response.body()?.isSuccess != true) {
                    throw Exception(response.body()?.message ?: "Błąd synchronizacji pozycji")
                }

                val items = response.body()
                    ?.data
                    ?.items
                    .orEmpty()
                    .map { dto ->
                        val item = dto.toOrderItemEntity()
                        tokenStore?.saveToken("orderItem", item.id, dto.token)
                        item
                    }

                database.orderDao().insertOrderItems(items)
            }
        }
    }

    private suspend fun syncUsers(preservedPasswords: Map<String, String> = emptyMap()) {
        val bootstrapResponse = orderService.getBootstrap()

        if (!bootstrapResponse.isSuccessful || bootstrapResponse.body()?.isSuccess != true) {
            throw Exception(bootstrapResponse.body()?.message ?: "Błąd pobierania manifestu użytkowników")
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
                throw Exception(response.body()?.message ?: "Błąd synchronizacji użytkowników")
            }

            val usersDto = response.body()
                ?.data
                ?.items
                .orEmpty()

            val users = mutableListOf<UsersEntity>()
            for (dto in usersDto) {
                val user = dto.toUsersEntity(preservedPasswords)
                tokenStore?.saveToken("user", user.id, dto.token)
                users.add(user)
            }

            database.userDao().insertUsers(users)
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
                throw Exception(response.body()?.message ?: "Błąd synchronizacji rezerwacji")
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

    suspend fun occupyTableRemote(tableId: UUID): Result<UUID> = withContext(Dispatchers.IO) {
        runCatching {
            val reservationSelection = getReservationForOccupyingTable(tableId)

            if (reservationSelection == null) {
                throw UnsupportedOperationException(
                    "Nie mozna zajac stolika bez aktualnej lub nadchodzacej rezerwacji."
                )
            }

            Log.d(
                "OCCUPY_TABLE",
                "Assigning waiter: tableId=$tableId, reservationToken=${reservationSelection.token}, reservationType=${reservationSelection.type}"
            )

            orderService.assignWaiterToReservation(reservationSelection.token)
                .requireApiSuccess("Nie udalo sie przypisac kelnera w API")

            syncOperationalData().getOrThrow()

            val orderId = database.orderDao().getActiveOrderForTableOnce(tableId)?.id
                ?: throw IllegalStateException("API przypisalo kelnera, ale synchronizacja nie zwrocila aktywnego zamowienia.")

            applyLocalTableStatus(tableId, "OCCUPIED")
            orderId
        }
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

    private suspend fun getReservationForOccupyingTable(tableId: UUID): OccupyReservationSelection? {
        syncReservations()

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
                "Using ${selection.type} reservation token=${selection.token} for tableId=$tableId"
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
            throw Exception(body?.message ?: ApiResponseLogic.errorMessageFromBody(rawError) ?: fallbackMessage)
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
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    } 

    private fun UserSyncDto.toUsersEntity(preservedPasswords: Map<String, String>): UsersEntity {
        val role = roleTokens.orEmpty().joinToString(",").ifBlank { if (isStaff) "ROLE_WAITER" else "ROLE_CLIENT" }

        return UsersEntity(
            id = token.toStableUUID(),
            username = username,
            password = preservedPasswords[username].orEmpty(),
            isActive = isActive ?: true,
            role = role,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
