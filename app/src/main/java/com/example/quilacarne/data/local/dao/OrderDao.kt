package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.relations.OrderItemWithDish
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    fun getItemsForOrder(orderId: UUID): Flow<List<OrderItemWithDish>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("SELECT * FROM orders WHERE table_id = :tableId AND deleted_at IS NULL LIMIT 1")
    fun getActiveOrderForTable(tableId: UUID): Flow<OrderEntity?>

    @Query("UPDATE orders SET status_id = :statusId WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: UUID, statusId: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>)
}