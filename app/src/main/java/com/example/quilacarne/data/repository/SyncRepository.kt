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
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.remote.models.CategoryDto
import com.example.quilacarne.data.remote.models.DishSyncDto
import com.example.quilacarne.data.remote.models.IngredientSyncDto
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

    private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray())

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    fun getTablesFlow(): Flow<List<RestaurantTableEntity>> {
        return database.restaurantTableDao().getAllTablesFlow()
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

                items.forEach { dto: TableDto ->
                    allTablesEntities.add(
                        RestaurantTableEntity(
                            id = dto.token.toStableUUID(),
                            tableNumber = dto.tableNumber,
                            capacity = dto.capacity,
                            statusId = dto.statusToken.takeIf { it.isNotBlank() }?.toStableUUID(),
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
                Log.d("SYNC", "✓ Zapisano ${allTablesEntities.size} stolików do bazy")
            } else {
                Log.w("SYNC", "UWAGA: Brak stolików z API!")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Błąd stolików: ", e)
            Result.failure(e)
        }
    }

    suspend fun syncMenu(): Result<Unit> {
        return try {
            val now = getCurrentTimestamp()
            val serverIp = "192.168.100.12"

            val catResponse = dishService.getCategories(lang = "pl")
            val categoriesDto: List<CategoryDto> =
                catResponse.body()?.data?.categories.orEmpty()

            val categoryEntities = categoriesDto.map { catDto: CategoryDto ->
                DishCategoryEntity(
                    id = catDto.token.toStableUUID(),
                    namePl = catDto.name,
                    nameEn = catDto.name,
                    createdAt = now,
                    updatedAt = now
                )
            }

            val ingSyncResponse = dishService.syncIngredients(page = 1)
            val globalIngredientEntities = mutableListOf<IngredientEntity>()
            val allergenEntities = mutableListOf<AllergenEntity>()
            val ingredientAllergenLinks = mutableListOf<IngredientAllergenEntity>()

            if (ingSyncResponse.isSuccessful) {
                val ingredientsDto: List<IngredientSyncDto> =
                    ingSyncResponse.body()?.data?.items.orEmpty()

                ingredientsDto.forEach { ingDto: IngredientSyncDto ->
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

                    ingDto.allergenTokens.orEmpty().forEach { allergenToken: String ->
                        val allergenId = allergenToken.toStableUUID()
                        allergenEntities.add(
                            AllergenEntity(
                                id = allergenId,
                                namePl = "Alergen $allergenToken",
                                nameEn = "Allergen $allergenToken",
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
            }

            val dishResponse = dishService.syncDishes(page = 1)
            val dishesDto: List<DishSyncDto> = dishResponse.body()?.data?.items.orEmpty()
            val dishEntities = mutableListOf<DishEntity>()
            val compositionEntities = mutableListOf<DishCompositionEntity>()

            dishesDto.forEach { dto: DishSyncDto ->
                val dishId = dto.token.toStableUUID()
                val matchingCategory = categoryEntities.find {
                    it.namePl.equals(dto.categoryName, ignoreCase = true)
                }

                dishEntities.add(
                    DishEntity(
                        id = dishId,
                        categoryId = matchingCategory?.id,
                        name = dto.name,
                        price = dto.price,
                        isAvailable = dto.isActive,
                        imageUrl = dto.imageUrl?.replace("localhost", serverIp),
                        createdAt = now,
                        updatedAt = now
                    )
                )

                dto.ingredients.orEmpty().forEach { ingDto ->
                    compositionEntities.add(
                        DishCompositionEntity(
                            dishId = dishId,
                            ingredientId = ingDto.token.toStableUUID(),
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

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Błąd menu: ", e)
            Result.failure(e)
        }
    }
}