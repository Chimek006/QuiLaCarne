package com.example.quilacarne.data.repository.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.GuestReportEntity
import com.example.quilacarne.data.local.entities.GuestReportStatusEntity
import com.example.quilacarne.data.local.entities.IngredientAllergenEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.OrderStatusEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.remote.dto.response.DishSyncDto
import com.example.quilacarne.data.remote.dto.response.GuestReportSyncDto
import com.example.quilacarne.data.remote.dto.response.IngredientSyncDto
import com.example.quilacarne.data.remote.dto.response.OrderItemSyncDto
import com.example.quilacarne.data.remote.dto.response.OrderSyncDto
import com.example.quilacarne.data.remote.dto.response.ReservationSyncDto
import com.example.quilacarne.data.remote.dto.response.SyncDictionaryDto
import com.example.quilacarne.data.remote.dto.response.TableDto
import com.example.quilacarne.data.remote.dto.response.UserSyncDto
import com.example.quilacarne.data.remote.dto.response.WebSocketEvent
import com.example.quilacarne.data.remote.network.RemoteTokenStore
import com.example.quilacarne.utils.ReservationTimeUtils
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

internal class WebSocketSyncHandler(
    private val database: AppDatabase,
    private val tokenStore: RemoteTokenStore?,
    private val gson: Gson,
    private val cacheDishImage: suspend (UUID, String?) -> String?
) {
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

    private fun storeReservationTokens(reservation: ReservationSyncDto) {
        val reservationId = reservation.token.toStableUUID()

        tokenStore?.saveToken("reservation", reservationId, reservation.token)
        tokenStore?.saveReservationUserToken(reservation.token, reservation.userToken)
    }
}
