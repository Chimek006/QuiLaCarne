package com.example.quilacarne.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.quilacarne.data.local.AppDatabase
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
import com.example.quilacarne.data.remote.models.TableDto
import com.example.quilacarne.data.remote.models.UserSyncDto
import com.example.quilacarne.data.remote.models.values
import com.example.quilacarne.data.remote.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SyncRepository(private val database: AppDatabase) {
    private val dishService = RetrofitClient.dishService
    private val tableService = RetrofitClient.tableService
    private val orderService = RetrofitClient.orderService

    private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray())

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    fun getTablesFlow(): Flow<List<RestaurantTableEntity>> {
        return database.restaurantTableDao().getAllTablesFlow()
    }

    fun getTableStatusesFlow(): Flow<List<TableStatusEntity>> {
        return database.tableStatusDao().getAllStatuses()
    }

    suspend fun syncAllLocalData(): Result<Unit> = runCatching {
        syncMenu().getOrThrow()
        syncTables().getOrThrow()
        syncUsers()
        syncOrdersAndItems()
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
                    val statusToken = dto.statusToken
                        ?: dto.statusTokens.firstOrNull()

                    if (!statusToken.isNullOrBlank()) {
                        tableStatusTokens.add(statusToken)
                    }

                    allTablesEntities.add(
                        RestaurantTableEntity(
                            id = dto.token.toStableUUID(),
                            tableNumber = dto.tableNumber,
                            capacity = dto.capacity,
                            statusId = statusToken?.toStableUUID(),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
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

                val orders = response.body()
                    ?.data
                    ?.items
                    .orEmpty()
                    .map { dto ->
                        dto.toOrderEntity()
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
                        dto.toOrderItemEntity()
                    }

                database.orderDao().insertOrderItems(items)
            }
        }
    }

    private suspend fun syncUsers() {
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
                users.add(dto.toUsersEntity())
            }

            database.userDao().insertUsers(users)
        }
    }

    suspend fun syncMenu(): Result<Unit> = runCatching {
        val now = getCurrentTimestamp()
        val serverIp = "192.168.100.12"

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
                    imageUrl = dto.imageUrl?.replace("localhost", serverIp),
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

    private suspend fun UserSyncDto.toUsersEntity(): UsersEntity {
        val existingUser = database.userDao().getUserByUsername(username)
        val role = roleTokens.orEmpty().joinToString(",").ifBlank { if (isStaff) "ROLE_WAITER" else "ROLE_CLIENT" }

        return UsersEntity(
            id = existingUser?.id ?: token.toStableUUID(),
            username = username,
            password = existingUser?.password.orEmpty(),
            isActive = isActive ?: true,
            role = role,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
