package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DishCategoryDao {
    @Query("SELECT * FROM dishes_categories WHERE deleted_at IS NULL")
    fun getAllCategories(): Flow<List<DishCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<DishCategoryEntity>)
}

