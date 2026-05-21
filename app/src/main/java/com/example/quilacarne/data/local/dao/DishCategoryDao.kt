package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface DishCategoryDao {
    @Query("SELECT * FROM dishes_categories WHERE deleted_at IS NULL")
    fun getAllCategories(): Flow<List<DishCategoryEntity>>

    @Query("SELECT * FROM dishes_categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryByIdOnce(categoryId: UUID): DishCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<DishCategoryEntity>)

    @Query("UPDATE dishes_categories SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :categoryId")
    suspend fun markCategoryDeleted(categoryId: UUID, deletedAt: String)
}

