package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.relations.DishWithIngredients
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface DishDao {
    @Query(
        "SELECT * FROM dishes " +
            "WHERE deleted_at IS NULL AND is_available = 1 AND name NOT LIKE 'DELETED_%'"
    )
    fun getAvailableDishes(): Flow<List<DishEntity>>

    @Transaction
    @Query(
        "SELECT * FROM dishes " +
            "WHERE id = :dishId AND deleted_at IS NULL AND is_available = 1 AND name NOT LIKE 'DELETED_%'"
    )
    fun getDishWithIngredients(dishId: UUID): Flow<DishWithIngredients?>

    @Transaction
    @Query(
        "SELECT * FROM dishes " +
            "WHERE deleted_at IS NULL AND is_available = 1 AND name NOT LIKE 'DELETED_%'"
    )
    fun getDishesWithIngredients(): Flow<List<DishWithIngredients>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDishes(dishes: List<DishEntity>)

    @Query("UPDATE dishes SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :dishId")
    suspend fun markDishDeleted(dishId: UUID, deletedAt: String)

    @Query(
        "UPDATE dishes SET deleted_at = :deletedAt, updated_at = :deletedAt " +
            "WHERE deleted_at IS NULL AND id NOT IN (:activeDishIds)"
    )
    suspend fun markDishesDeletedExcept(activeDishIds: List<UUID>, deletedAt: String)

    @Query("UPDATE dishes SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE deleted_at IS NULL")
    suspend fun markAllDishesDeleted(deletedAt: String)

    @Query(
        "UPDATE dishes SET is_available = 0, updated_at = :updatedAt " +
            "WHERE id IN (SELECT dish_id FROM x_dish_composition WHERE ingredient_id = :ingredientId)"
    )
    suspend fun markDishesUnavailableForIngredient(ingredientId: UUID, updatedAt: String)

    @Query("DELETE FROM x_dish_composition WHERE dish_id = :dishId")
    suspend fun deleteCompositionsForDish(dishId: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComposition(composition: List<DishCompositionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompositions(compositions: List<DishCompositionEntity>)
}
