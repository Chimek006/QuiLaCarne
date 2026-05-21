package com.example.quilacarne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.quilacarne.data.local.entities.OrderStatusEntity
import java.util.UUID

@Dao
interface OrderStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<OrderStatusEntity>)

    @Query("UPDATE order_status SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :statusId")
    suspend fun markStatusDeleted(statusId: UUID, deletedAt: String)
}
