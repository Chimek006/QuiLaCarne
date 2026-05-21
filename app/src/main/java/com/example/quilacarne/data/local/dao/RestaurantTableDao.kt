package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface RestaurantTableDao {
    @Query("SELECT * FROM restaurant_tables")
    fun getAllTablesFlow(): Flow<List<RestaurantTableEntity>>

    @Query("SELECT * FROM restaurant_tables")
    suspend fun getAllTablesOnce(): List<RestaurantTableEntity>

    @Query("SELECT * FROM restaurant_tables WHERE id = :tableId LIMIT 1")
    fun getTableById(tableId: UUID): Flow<RestaurantTableEntity?>

    @Query("SELECT * FROM restaurant_tables WHERE id = :tableId LIMIT 1")
    suspend fun getTableByIdOnce(tableId: UUID): RestaurantTableEntity?

    @Query("UPDATE restaurant_tables SET status_id = :statusId, updated_at = :updatedAt WHERE id = :tableId")
    suspend fun updateStatus(tableId: UUID, statusId: UUID?, updatedAt: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<RestaurantTableEntity>)

    @Query("DELETE FROM restaurant_tables WHERE id NOT IN (:tableIds)")
    suspend fun deleteTablesExcept(tableIds: List<UUID>)

    @Query("DELETE FROM restaurant_tables WHERE id = :tableId")
    suspend fun deleteTableById(tableId: UUID)

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
