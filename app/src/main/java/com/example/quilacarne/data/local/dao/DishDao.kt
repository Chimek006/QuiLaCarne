package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface DishDao {
    @Query("SELECT * FROM dishes WHERE is_available = 1 AND deleted_at IS NULL")
    fun getAvailableDishes(): Flow<List<DishEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDishes(dishes: List<DishEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComposition(composition: List<DishCompositionEntity>)
}