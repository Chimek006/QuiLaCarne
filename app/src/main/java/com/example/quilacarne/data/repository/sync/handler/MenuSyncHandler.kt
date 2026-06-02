package com.example.quilacarne.data.repository.sync.handler

import androidx.room.withTransaction
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.IngredientAllergenEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.remote.service.DishService
import com.example.quilacarne.data.remote.dto.response.DishSyncDto
import com.example.quilacarne.data.remote.dto.response.IngredientSyncDto
import com.example.quilacarne.data.remote.store.RemoteTokenStore
import com.example.quilacarne.data.repository.sync.getCurrentTimestamp
import com.example.quilacarne.data.repository.sync.toStableUUID
import java.util.UUID

internal class MenuSyncHandler(
    private val database: AppDatabase,
    private val dishService: DishService,
    private val tokenStore: RemoteTokenStore?,
    private val cacheDishImage: suspend (UUID, String?) -> String?
) {
    suspend fun syncMenu(): Result<Unit> = runCatching {
        val now = getCurrentTimestamp()
        val categoryEntities = fetchCategoryEntities(now)
        val allergenNames = fetchAllergenNamesByToken()
        val ingredientSync = buildIngredientSync(fetchIngredientDtos(), allergenNames, now)
        val dishSync = buildDishSync(fetchDishDtos(), now)

        database.withTransaction {
            database.dishCategoryDao().insertAll(categoryEntities)
            database.ingredientDao().insertIngredients(ingredientSync.first)
            database.ingredientDao().insertAllergens(ingredientSync.second)
            database.ingredientDao().insertIngredientAllergens(ingredientSync.third)
            markDishesMissingFromSyncDeleted(dishSync.first.map { it.id }, now)
            database.dishDao().insertDishes(dishSync.first)
            database.dishDao().insertCompositions(dishSync.second)
        }
    }

    private suspend fun fetchCategoryEntities(now: String): List<DishCategoryEntity> {
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

        return (categoriesPlByToken.keys + categoriesEnByToken.keys)
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
    }

    private suspend fun fetchAllergenNamesByToken(): Pair<Map<String, String>, Map<String, String>> {
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

        return allergensPlByToken to allergensEnByToken
    }

    private suspend fun fetchIngredientDtos(): List<IngredientSyncDto> {
        return fetchPagedItems { page ->
            val response = dishService.syncIngredients(page = page)
            if (response.isSuccessful) response.body()?.data?.items.orEmpty() else null
        }
    }

    private suspend fun fetchDishDtos(): List<DishSyncDto> {
        return fetchPagedItems { page ->
            val response = dishService.syncDishes(page = page)
            if (!response.isSuccessful) {
                throw IllegalStateException("Nie udalo sie pobrac strony dan menu: $page")
            }
            response.body()?.data?.items.orEmpty()
        }
    }

    private suspend fun markDishesMissingFromSyncDeleted(
        syncedDishIds: List<UUID>,
        now: String
    ) {
        if (syncedDishIds.isEmpty()) {
            database.dishDao().markAllDishesDeleted(now)
        } else {
            database.dishDao().markDishesDeletedExcept(syncedDishIds, now)
        }
    }

    private suspend fun <T> fetchPagedItems(fetchPage: suspend (Int) -> List<T>?): List<T> {
        val allItems = mutableListOf<T>()
        var page = 1
        var hasMoreItems = true

        while (hasMoreItems) {
            val items = fetchPage(page).orEmpty()
            hasMoreItems = items.isNotEmpty()
            if (hasMoreItems) {
                allItems.addAll(items)
                page++
            }
        }

        return allItems
    }

    private fun buildIngredientSync(
        ingredients: List<IngredientSyncDto>,
        allergenNames: Pair<Map<String, String>, Map<String, String>>,
        now: String
    ): Triple<List<IngredientEntity>, List<AllergenEntity>, List<IngredientAllergenEntity>> {
        val ingredientEntities = mutableListOf<IngredientEntity>()
        val allergenEntities = mutableListOf<AllergenEntity>()
        val ingredientAllergenLinks = mutableListOf<IngredientAllergenEntity>()

        ingredients.forEach { ingredient ->
            val ingredientId = ingredient.token.toStableUUID()
            ingredientEntities.add(ingredient.toIngredientEntity(ingredientId, now))
            ingredient.allergenTokens.orEmpty().forEach { allergenToken ->
                val allergenId = allergenToken.toStableUUID()
                allergenEntities.add(allergenToken.toAllergenEntity(allergenId, allergenNames, now))
                ingredientAllergenLinks.add(ingredientId.toAllergenLink(allergenId, now))
            }
        }

        return Triple(ingredientEntities, allergenEntities, ingredientAllergenLinks)
    }

    private suspend fun buildDishSync(
        dishes: List<DishSyncDto>,
        now: String
    ): Pair<List<DishEntity>, List<DishCompositionEntity>> {
        val dishEntities = mutableListOf<DishEntity>()
        val compositionEntities = mutableListOf<DishCompositionEntity>()

        dishes.forEach { dish ->
            val dishId = dish.token.toStableUUID()
            tokenStore?.saveToken("dish", dishId, dish.token)
            dishEntities.add(dish.toDishEntity(dishId, now))
            compositionEntities.addAll(dish.toCompositionEntities(dishId, now))
        }

        return dishEntities to compositionEntities
    }

    private fun IngredientSyncDto.toIngredientEntity(ingredientId: UUID, now: String): IngredientEntity {
        return IngredientEntity(
            id = ingredientId,
            namePl = namePl,
            nameEn = nameEn,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun String.toAllergenEntity(
        allergenId: UUID,
        allergenNames: Pair<Map<String, String>, Map<String, String>>,
        now: String
    ): AllergenEntity {
        val (allergensPlByToken, allergensEnByToken) = allergenNames
        return AllergenEntity(
            id = allergenId,
            namePl = allergensPlByToken[this] ?: allergensEnByToken[this] ?: this,
            nameEn = allergensEnByToken[this] ?: allergensPlByToken[this] ?: this,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun UUID.toAllergenLink(allergenId: UUID, now: String): IngredientAllergenEntity {
        return IngredientAllergenEntity(
            ingredientId = this,
            allergenId = allergenId,
            createdAt = now,
            updatedAt = now
        )
    }

    private suspend fun DishSyncDto.toDishEntity(dishId: UUID, now: String): DishEntity {
        val categoryId = categoryToken
            ?.takeIf { it.isNotBlank() }
            ?.toStableUUID()
        val deleted = isDeleted == true || deletedAt != null || name.startsWith("DELETED_", ignoreCase = true)
        val available = isAvailable ?: true

        return DishEntity(
            id = dishId,
            categoryId = categoryId,
            name = name,
            price = price,
            isAvailable = available && !deleted,
            imageUrl = cacheDishImage(dishId, imageUrl),
            createdAt = now,
            updatedAt = now,
            deletedAt = if (deleted) deletedAt ?: now else null
        )
    }

    private fun DishSyncDto.toCompositionEntities(dishId: UUID, now: String): List<DishCompositionEntity> {
        return ingredientTokens.orEmpty().map { ingredientToken ->
            DishCompositionEntity(
                dishId = dishId,
                ingredientId = ingredientToken.toStableUUID(),
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
