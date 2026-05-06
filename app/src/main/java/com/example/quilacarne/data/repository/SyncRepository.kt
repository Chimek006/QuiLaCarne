package com.example.quilacarne.data.repository

import android.util.Log
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.remote.network.RetrofitClient
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncRepository(private val database: AppDatabase) {
    private val dishService = RetrofitClient.dishService

    private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(this.toByteArray())

    private fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

    suspend fun syncMenu(): Result<Unit> {
        return try {
            val now = getCurrentTimestamp()
            val serverIp = "192.168.100.12"

            val catResponse = dishService.getCategories(lang = "pl")
            val categoriesDto = catResponse.body()?.data?.categories ?: emptyList()
            val categoryEntities = categoriesDto.map {
                DishCategoryEntity(
                    id = it.token.toStableUUID(),
                    namePl = it.name,
                    nameEn = it.name,
                    createdAt = now,
                    updatedAt = now
                )
            }
            database.dishCategoryDao().insertAll(categoryEntities)

            val ingSyncResponse = dishService.syncIngredients(page = 1)
            if (ingSyncResponse.isSuccessful) {
                val ingredientsDto = ingSyncResponse.body()?.data?.items ?: emptyList()
                val globalIngredientEntities = ingredientsDto.map {
                    IngredientEntity(
                        id = it.token.toStableUUID(),
                        namePl = it.namePl,
                        nameEn = it.nameEn,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                database.ingredientDao().insertAll(globalIngredientEntities)
            }

            val dishResponse = dishService.syncDishes(page = 1)
            if (!dishResponse.isSuccessful || dishResponse.body()?.isSuccess == false) {
                return Result.failure(Exception("Błąd dań: ${dishResponse.message()}"))
            }

            val dishesDto = dishResponse.body()?.data?.items ?: emptyList()

            val dishEntities = mutableListOf<DishEntity>()
            val compositionEntities = mutableListOf<DishCompositionEntity>()
            val inlineIngredientEntities = mutableListOf<IngredientEntity>()

            dishesDto.forEach { dto ->
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

                val ingredientsList = dto.ingredients ?: emptyList()
                ingredientsList.forEach { ingDto ->
                    val ingredientId = ingDto.token.toStableUUID()

                    compositionEntities.add(
                        DishCompositionEntity(
                            dishId = dishId,
                            ingredientId = ingredientId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )

                    inlineIngredientEntities.add(
                        IngredientEntity(
                            id = ingredientId,
                            namePl = ingDto.name,
                            nameEn = ingDto.name,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            database.dishDao().insertDishes(dishEntities)
            database.ingredientDao().insertAll(inlineIngredientEntities)
            database.dishDao().insertCompositions(compositionEntities)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SYNC_FATAL", "Błąd krytyczny synchronizacji: ${e.message}")
            Result.failure(e)
        }
    }
}