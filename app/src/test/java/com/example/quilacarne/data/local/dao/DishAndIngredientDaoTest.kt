package com.example.quilacarne.data.local.dao

import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.IngredientAllergenEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.remote.dto.response.DishSyncDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DishAndIngredientDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var categoryId: UUID
    private lateinit var dishId: UUID
    private lateinit var ingredientId: UUID
    private lateinit var allergenId: UUID

    @Before
    fun setUp() = runTest {
        db = TestDatabaseFactory.create()
        categoryId = UUID.randomUUID()
        dishId = UUID.randomUUID()
        ingredientId = UUID.randomUUID()
        allergenId = UUID.randomUUID()

        db.dishCategoryDao().insertAll(
            listOf(
                DishCategoryEntity(
                    id = categoryId,
                    namePl = "Makarony",
                    nameEn = "Pasta",
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun dishDaoReturnsInsertedDishes() = runTest {
        val dish = dish()

        db.dishDao().insertDishes(listOf(dish))

        assertEquals(listOf(dish), db.dishDao().getAvailableDishes().first())
    }

    @Test
    fun dishDaoHidesUnavailableDeletedAndDeletedNameDishes() = runTest {
        val available = dish()
        val unavailable = dish(
            id = UUID.randomUUID(),
            name = "Unavailable",
            isAvailable = false
        )
        val deleted = dish(
            id = UUID.randomUUID(),
            name = "Deleted",
            deletedAt = "deleted"
        )
        val deletedName = dish(
            id = UUID.randomUUID(),
            name = "DELETED_Carbonara"
        )

        db.dishDao().insertDishes(listOf(available, unavailable, deleted, deletedName))

        assertEquals(listOf(available), db.dishDao().getAvailableDishes().first())
    }

    @Test
    fun dishDaoMarksDishesMissingFromSyncAsDeleted() = runTest {
        val synced = dish()
        val missing = dish(id = UUID.randomUUID(), name = "Removed")

        db.dishDao().insertDishes(listOf(synced, missing))
        db.dishDao().markDishesDeletedExcept(listOf(synced.id), "deleted")

        assertEquals(listOf(synced), db.dishDao().getAvailableDishes().first())
    }

    @Test
    fun dishSyncDtoAcceptsAvailableAliasFromApi() {
        val dto = Gson().fromJson(
            """
                {
                  "token": "dish-token",
                  "name": "Carbonara",
                  "price": 4200,
                  "available": true,
                  "imageUrl": null,
                  "categoryToken": null,
                  "ingredientTokens": []
                }
            """.trimIndent(),
            DishSyncDto::class.java
        )

        assertEquals(true, dto.isAvailable)
    }

    @Test
    fun ingredientDaoReturnsIngredientsAndAllergensOrderedByPolishName() = runTest {
        val ingredient = ingredient()
        val gluten = allergen(allergenId, "Gluten")
        val lactose = allergen(UUID.randomUUID(), "Laktoza")

        db.ingredientDao().insertIngredients(listOf(ingredient))
        db.ingredientDao().insertAllergens(listOf(lactose, gluten))

        assertEquals(listOf(ingredient), db.ingredientDao().getAllIngredients().first())
        assertEquals(listOf(gluten, lactose), db.ingredientDao().getAllAllergens().first())
    }

    @Test
    fun dishWithIngredientsIncludesNestedAllergens() = runTest {
        db.dishDao().insertDishes(listOf(dish()))
        db.ingredientDao().insertIngredients(listOf(ingredient()))
        db.ingredientDao().insertAllergens(listOf(allergen(allergenId, "Gluten")))
        db.dishDao().insertCompositions(
            listOf(
                DishCompositionEntity(
                    dishId = dishId,
                    ingredientId = ingredientId,
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
        db.ingredientDao().insertIngredientAllergens(
            listOf(
                IngredientAllergenEntity(
                    ingredientId = ingredientId,
                    allergenId = allergenId,
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )

        val details = db.dishDao().getDishWithIngredients(dishId).first()

        assertEquals("Carbonara", details?.dish?.name)
        assertEquals("Boczek", details?.ingredientsWithAllergens?.single()?.ingredient?.namePl)
        assertEquals("Gluten", details?.ingredientsWithAllergens?.single()?.allergens?.single()?.namePl)
    }

    private fun dish(
        id: UUID = dishId,
        name: String = "Carbonara",
        isAvailable: Boolean = true,
        deletedAt: String? = null
    ): DishEntity {
        return DishEntity(
            id = id,
            categoryId = categoryId,
            name = name,
            price = 4200,
            isAvailable = isAvailable,
            imageUrl = null,
            createdAt = "created",
            updatedAt = "updated",
            deletedAt = deletedAt
        )
    }

    private fun ingredient(): IngredientEntity {
        return IngredientEntity(
            id = ingredientId,
            namePl = "Boczek",
            nameEn = "Bacon",
            createdAt = "created",
            updatedAt = "updated"
        )
    }

    private fun allergen(id: UUID, namePl: String): AllergenEntity {
        return AllergenEntity(
            id = id,
            namePl = namePl,
            nameEn = namePl,
            createdAt = "created",
            updatedAt = "updated"
        )
    }
}
