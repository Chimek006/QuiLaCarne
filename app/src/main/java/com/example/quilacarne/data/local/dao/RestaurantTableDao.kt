package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantTableDao {
    @Query("SELECT * FROM restaurant_tables")
    fun getAllTablesFlow(): Flow<List<RestaurantTableEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<RestaurantTableEntity>)

    @Query("DELETE FROM restaurant_tables")
    suspend fun deleteAllTables()

    @Query("DELETE FROM restaurant_tables")
    suspend fun clearAll()

    @Transaction
    suspend fun syncTables(tables: List<RestaurantTableEntity>) {
        deleteAllTables()
        insertTables(tables)
    }
}