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
import com.example.quilacarne.data.remote.models.CategoryDto
import com.example.quilacarne.data.remote.models.DishSyncDto
import com.example.quilacarne.data.remote.models.IngredientSyncDto
import com.example.quilacarne.data.remote.models.OrderItemSyncDto
import com.example.quilacarne.data.remote.models.OrderSyncDto
import com.example.quilacarne.data.remote.models.TableDto
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

    suspend fun syncAllLocalData(): Result<Unit> = runCatching {
        syncMenu().getOrThrow()
        syncTables().getOrThrow()
        syncOrderItemStatuses()
        syncOrdersAndItems()
    }

    suspend fun syncTables(): Result<Unit> {
        return try {
            val now = getCurrentTimestamp()
            val dao = database.restaurantTableDao()

            var page = 1
            val allTablesEntities = mutableListOf<RestaurantTableEntity>()

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
                    allTablesEntities.add(
                        RestaurantTableEntity(
                            id = dto.token.toStableUUID(),
                            tableNumber = dto.tableNumber,
                            capacity = dto.capacity,
                            statusId = null,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                page++
            }

            if (allTablesEntities.isNotEmpty()) {
                database.withTransaction {
                    dao.clearAll()
                    dao.insertTables(allTablesEntities)
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

    private suspend fun syncOrderItemStatuses() {
        val response = orderService.getOrderItemStatuses()

        if (!response.isSuccessful || response.body()?.isSuccess != true) {
            throw Exception(response.body()?.message ?: "Błąd pobierania statusów")
        }

        val statusEntities = response.body()
            ?.data
            .orEmpty()
            .map { dto ->
                TableStatusEntity(
                    id = dto.token.toStableUUID(),
                    token = dto.token,
                    namePl = dto.name,
                    nameEn = dto.name,
                    createdAt = "",
                    updatedAt = ""
                )
            }

        database.tableStatusDao().insertAll(statusEntities)
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
            waiterId = null,
            statusId = null,
            totalPrice = totalPrice.toInt(),
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
            priceAtTimeOfOrder = priceAtTimeOfOrder.toInt(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
