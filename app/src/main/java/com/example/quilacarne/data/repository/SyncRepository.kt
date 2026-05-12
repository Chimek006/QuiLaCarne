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
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.models.CategoryDto
import com.example.quilacarne.data.remote.models.DishSyncDto
import com.example.quilacarne.data.remote.models.IngredientSyncDto
import com.example.quilacarne.data.remote.models.OrderItemSyncDto
import com.example.quilacarne.data.remote.models.OrderSyncDto
import com.example.quilacarne.data.remote.models.ReportCreateRequest
import com.example.quilacarne.data.remote.models.ReservationCreateRequest
import com.example.quilacarne.data.remote.models.ReservationDishRequest
import com.example.quilacarne.data.remote.models.ReservationSyncDto
import com.example.quilacarne.data.remote.models.TableDto
import com.example.quilacarne.data.remote.models.UserSyncDto
import com.example.quilacarne.data.remote.models.values
import com.example.quilacarne.data.remote.network.ApiLoggingInterceptor
import com.example.quilacarne.data.remote.network.AuthInterceptor
import com.example.quilacarne.data.remote.network.RemoteTokenStore
import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.network.TokenAuthenticator
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
import java.util.Calendar
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
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    fun getTablesFlow(): Flow<List<RestaurantTableEntity>> {
        return database.restaurantTableDao().getAllTablesFlow()
    }

    fun getTableStatusesFlow(): Flow<List<TableStatusEntity>> {
        return database.tableStatusDao().getAllStatuses()
    }

    fun getOrdersFlow(): Flow<List<OrderEntity>> {
        return database.orderDao().getAllOrders()
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
                    val statusToken = dto.currentStatusToken()
                        ?: existingStatusTokensByTableId[tableId]

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
                            updatedAt = now
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
                                nameEn = token.toTableStatusName(),
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
        val response = tableService.getStatusDictionary()

        if (!response.isSuccessful || response.body()?.isSuccess != true) {
            throw Exception(response.body()?.message ?: "Błąd pobierania statusów")
        }

        val statusEntities = response.body()
            ?.data
            .values()
            .map { dto ->
                TableStatusEntity(
                    id = dto.token.toStableUUID(),
                    token = dto.token,
                    namePl = dto.name,
                    nameEn = dto.name,
                    createdAt = now,
                    updatedAt = now
                )
            }

        database.tableStatusDao().insertAll(statusEntities)
    }

    private fun TableDto.currentStatusToken(): String? {
        val tokens = buildList {
            statusToken?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(statusTokens.filter { it.isNotBlank() })
        }

        if (tokens.isEmpty()) return null

        val tokensByName = tokens.associateBy { it.uppercase(Locale.US) }
        val priority = listOf(
            "OUT_OF_SERVICE",
            "CLEANING",
            "OCCUPIED",
            "RESERVED",
            "AVAILABLE"
        )

        priority.forEach { preferredToken ->
            tokensByName[preferredToken]?.let { return it }
        }

        return tokens.first()
    }

    private fun String.toTableStatusName(): String {
        return when (uppercase()) {
            "AVAILABLE" -> "Wolny"
            "OCCUPIED" -> "Zajęty"
            "RESERVED" -> "Zarezerwowany"
            "CLEANING" -> "Do sprzątnięcia"
            "OUT_OF_SERVICE" -> "Wyłączony"
            else -> lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
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
        val now = Date()
        val latestActiveReservationByTable = mutableMapOf<UUID, ReservationSyncDto>()

        while (true) {
            val response = orderService.syncReservations(page = page)

            if (!response.isSuccessful || response.body()?.isSuccess != true) {
                throw Exception(response.body()?.message ?: "Błąd synchronizacji rezerwacji")
            }

            val reservations = response.body()?.data?.items.orEmpty()
            if (reservations.isEmpty()) break

            reservations.forEach { reservation ->
                storeReservationTokens(reservation)

                if (reservation.isCurrentActiveReservation(now)) {
                    val tableId = reservation.tableToken.toStableUUID()
                    val current = latestActiveReservationByTable[tableId]

                    if (current == null || reservation.updatedAt >= current.updatedAt) {
                        latestActiveReservationByTable[tableId] = reservation
                    }
                }
            }

            page++
        }

        latestActiveReservationByTable.forEach { (tableId, reservation) ->
            tokenStore?.saveTableReservationToken(tableId, reservation.token)
        }
    }

    private fun storeReservationTokens(reservation: ReservationSyncDto) {
        val reservationId = reservation.token.toStableUUID()

        tokenStore?.saveToken("reservation", reservationId, reservation.token)
        tokenStore?.saveReservationUserToken(reservation.token, reservation.userToken)
    }

    private fun ReservationSyncDto.isActiveReservation(): Boolean {
        val inactiveStatuses = setOf(
            "ABSENT",
            "CANCELED",
            "CANCELLED",
            "COMPLETED",
            "DONE",
            "FINISHED"
        )

        return statusTokens.none { it.uppercase() in inactiveStatuses }
    }

    private fun ReservationSyncDto.isCurrentActiveReservation(now: Date): Boolean {
        if (!isActiveReservation()) return false

        val start = parseApiTimestamp(startTime)
        val end = parseApiTimestamp(endTime)

        val hasStarted = start == null || !now.before(start)
        val hasNotEnded = end == null || now.before(end)

        return hasStarted && hasNotEnded
    }

    suspend fun syncMenu(): Result<Unit> = runCatching {
        val now = getCurrentTimestamp()

        val catResponse = dishService.getCategories(lang = "pl")
        val categoriesDto: List<CategoryDto> = catResponse.body()?.data?.item.orEmpty()

        val allergenResponse = dishService.getAllergens(lang = "pl")
        val allergensByToken = allergenResponse.body()
            ?.data
            ?.item
            .orEmpty()
            .associate { allergenDto ->
                allergenDto.token to allergenDto.name
            }

        val categoryEntities = categoriesDto.map { catDto ->
            DishCategoryEntity(
                id = catDto.token.toStableUUID(),
                namePl = catDto.name,
                nameEn = catDto.name,
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
                        namePl = allergensByToken[allergenToken] ?: allergenToken,
                        nameEn = allergensByToken[allergenToken] ?: allergenToken,
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
            val tableToken = getRemoteToken("table", tableId)

            val response = when (statusToken.uppercase()) {
                "AVAILABLE" -> {
                    releaseActiveTableReservation(tableId)
                    syncOperationalData()
                        .onFailure { error ->
                            Log.w("SYNC", "Nie udalo sie odswiezyc stolikow po zwolnieniu: ${error.message}")
                        }
                    applyLocalTableStatus(tableId, statusToken)
                    return@runCatching
                }
                "CLEANING" -> tableService.markTableCleaning(tableToken)
                "OUT_OF_SERVICE" -> tableService.markTableOutOfService(tableToken)
                "RESERVED" -> createRemoteReservation(tableToken)
                else -> throw UnsupportedOperationException(
                    "API nie udostepnia bezposredniej zmiany statusu stolika na $statusToken"
                )
            }

            response.requireApiSuccess("Nie udalo sie zapisac statusu stolika w API")
            syncOperationalData()
                .onFailure { error ->
                    Log.w("SYNC", "Nie udalo sie odswiezyc stolikow po zmianie statusu: ${error.message}")
                }
            applyLocalTableStatus(tableId, statusToken)
        }
    }

    suspend fun occupyTableRemote(tableId: UUID): Result<UUID> = withContext(Dispatchers.IO) {
        runCatching {
            val tableToken = getRemoteToken("table", tableId)

            tokenStore?.clearTableReservationToken(tableId)

            val createResponse = createRemoteReservation(tableToken)
            if (!createResponse.isSuccessful && createResponse.code() != 409) {
                createResponse.requireApiSuccess("Nie udalo sie utworzyc rezerwacji w API")
            }

            syncReservations()

            val reservationToken = tokenStore?.getTableReservationToken(tableId)

            if (reservationToken.isNullOrBlank()) {
                createResponse.requireApiSuccess("Nie udalo sie utworzyc rezerwacji w API")
                throw IllegalStateException("Brak tokena rezerwacji dla wybranego stolika")
            }

            orderService.assignWaiterToReservation(reservationToken)
                .requireApiSuccess("Nie udalo sie przypisac kelnera w API")

            syncOperationalData()
                .onFailure { error ->
                    Log.w("SYNC", "Nie udalo sie odswiezyc stolika po zajeciu: ${error.message}")
                }
            applyLocalTableStatus(tableId, "OCCUPIED")

            database.orderDao().getActiveOrderForTableOnce(tableId)?.id
                ?: createLocalOrderPlaceholder(tableId, reservationToken)
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

    private suspend fun releaseActiveTableReservation(tableId: UUID) {
        val reservationToken = getActiveReservationTokenForTable(tableId)
        if (reservationToken.isNullOrBlank()) {
            Log.w("TABLE_REMOTE", "Brak aktywnej rezerwacji do zwolnienia stolika $tableId")
            return
        }

        orderService.markReservationAbsent(reservationToken)
            .requireApiSuccess("Nie udalo sie zwolnic stolika w API")
        tokenStore?.clearTableReservationToken(tableId)
    }

    private suspend fun getActiveReservationTokenForTable(tableId: UUID): String? {
        val activeOrder = database.orderDao().getActiveOrderForTableOnce(tableId)
        if (activeOrder != null) {
            tokenStore?.getOrderReservationToken(activeOrder.id)?.let { return it }
        }

        tokenStore?.getTableReservationToken(tableId)?.let { return it }

        syncReservations()
        return tokenStore?.getTableReservationToken(tableId)
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

    private suspend fun createRemoteReservation(
        tableToken: String
    ): Response<com.example.quilacarne.data.remote.models.ApiResponse<Unit>> {
        val start = Date()
        val end = Calendar.getInstance().apply {
            time = start
            add(Calendar.HOUR_OF_DAY, 2)
        }.time

        return orderService.createReservation(
            ReservationCreateRequest(
                tableToken = tableToken,
                startTime = formatApiTimestamp(start),
                endTime = formatApiTimestamp(end)
            )
        )
    }

    private suspend fun getReservationTokenForOrder(orderId: UUID): String {
        tokenStore?.getOrderReservationToken(orderId)?.let { return it }

        syncOperationalData().getOrThrow()

        tokenStore?.getOrderReservationToken(orderId)?.let { return it }

        throw IllegalStateException("Brak tokena rezerwacji dla zamowienia")
    }

    private suspend fun createLocalOrderPlaceholder(
        tableId: UUID,
        reservationToken: String
    ): UUID {
        val now = getCurrentTimestamp()
        val orderId = reservationToken.toStableUUID()

        database.orderDao().insertOrder(
            OrderEntity(
                id = orderId,
                tableId = tableId,
                waiterId = null,
                statusId = null,
                totalPrice = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        tokenStore?.saveOrderReservationToken(orderId, reservationToken)

        return orderId
    }

    private fun getRemoteToken(type: String, localId: UUID): String {
        return tokenStore?.getToken(type, localId)
            ?: throw IllegalStateException("Brak tokena API dla $type $localId")
    }

    private fun <T> Response<com.example.quilacarne.data.remote.models.ApiResponse<T>>.requireApiSuccess(
        fallbackMessage: String
    ) {
        val body = body()
        if (!isSuccessful) {
            throw Exception(body?.message ?: errorBody()?.string() ?: fallbackMessage)
        }

        if (body == null) return

        val hasErrorMessages = body.errorMessages?.isNotEmpty() == true
        val statusCodeLooksSuccessful = body.statusCode in 200..299
        val wrapperLooksSuccessful = body.isSuccess || statusCodeLooksSuccessful || (!hasErrorMessages && body.statusCode == 0)

        if (!wrapperLooksSuccessful) {
            throw Exception(body.message ?: body.errorMessages?.joinToString() ?: fallbackMessage)
        }
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
                    nameEn = statusToken.toTableStatusName(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        database.restaurantTableDao().updateStatus(tableId, statusId, now)
    }

    private fun formatApiTimestamp(date: Date): String =
        apiTimestampFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)

    private fun parseApiTimestamp(value: String): Date? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        patterns.forEach { pattern ->
            runCatching { apiTimestampFormat(pattern).parse(value) }
                .getOrNull()
                ?.let { return it }
        }

        return null
    }

    private fun apiTimestampFormat(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
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
