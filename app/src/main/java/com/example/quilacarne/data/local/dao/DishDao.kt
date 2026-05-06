package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import com.example.quilacarne.data.local.relations.DishWithIngredients
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface DishDao {
    @Query("SELECT * FROM dishes")
    fun getAvailableDishes(): Flow<List<DishEntity>>

    @Transaction
    @Query("SELECT * FROM dishes WHERE id = :dishId")
    fun getDishWithIngredients(dishId: UUID): Flow<DishWithIngredients?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDishes(dishes: List<DishEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComposition(composition: List<DishCompositionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompositions(compositions: List<DishCompositionEntity>)
}