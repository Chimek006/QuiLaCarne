package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.IngredientAllergenEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface IngredientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllergens(allergens: List<AllergenEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredientAllergens(links: List<IngredientAllergenEntity>)

    @Query("UPDATE ingredients SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :ingredientId")
    suspend fun markIngredientDeleted(ingredientId: UUID, deletedAt: String)

    @Query("UPDATE allergens SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :allergenId")
    suspend fun markAllergenDeleted(allergenId: UUID, deletedAt: String)

    @Query("DELETE FROM x_ingredient_allergens WHERE ingredient_id = :ingredientId")
    suspend fun deleteAllergenLinksForIngredient(ingredientId: UUID)

    @Query("SELECT id FROM allergens WHERE id IN (:allergenIds)")
    suspend fun getExistingAllergenIds(allergenIds: List<UUID>): List<UUID>

    @Query("SELECT id FROM ingredients WHERE id IN (:ingredientIds)")
    suspend fun getExistingIngredientIds(ingredientIds: List<UUID>): List<UUID>

    @Query("SELECT * FROM ingredients")
    fun getAllIngredients(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM allergens WHERE deleted_at IS NULL ORDER BY name_pl")
    fun getAllAllergens(): Flow<List<AllergenEntity>>
}
