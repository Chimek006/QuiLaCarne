package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.relations.OrderItemWithDish
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
@Suppress("TooManyFunctions")
interface OrderDao {
    @Query("SELECT * FROM orders WHERE deleted_at IS NULL ORDER BY updated_at DESC, created_at DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE deleted_at IS NULL ORDER BY updated_at DESC, created_at DESC")
    suspend fun getAllOrdersOnce(): List<OrderEntity>

    @Transaction
    @Query("SELECT * FROM order_items WHERE order_id = :orderId AND deleted_at IS NULL")
    fun getItemsForOrder(orderId: UUID): Flow<List<OrderItemWithDish>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Query("DELETE FROM orders WHERE id NOT IN (:orderIds)")
    suspend fun deleteOrdersExcept(orderIds: List<UUID>)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrderById(orderId: UUID)

    @Query("DELETE FROM orders")
    suspend fun clearOrders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE id NOT IN (:itemIds)")
    suspend fun deleteOrderItemsExcept(itemIds: List<UUID>)

    @Query("DELETE FROM order_items")
    suspend fun clearOrderItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity)

    @Query("SELECT * FROM orders WHERE table_id = :tableId AND deleted_at IS NULL ORDER BY updated_at DESC, created_at DESC LIMIT 1")
    fun getActiveOrderForTable(tableId: UUID): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE table_id = :tableId AND deleted_at IS NULL ORDER BY updated_at DESC, created_at DESC LIMIT 1")
    suspend fun getActiveOrderForTableOnce(tableId: UUID): OrderEntity?

    @Query("UPDATE orders SET status_id = :statusId WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: UUID, statusId: UUID)

    @Query("UPDATE orders SET waiter_id = :waiterId, updated_at = :updatedAt WHERE id = :orderId")
    suspend fun updateOrderWaiter(orderId: UUID, waiterId: UUID, updatedAt: String)

    @Query(
        "UPDATE orders SET table_id = :newTableId, updated_at = :updatedAt " +
            "WHERE id = :orderId AND deleted_at IS NULL"
    )
    suspend fun moveOrderToTable(
        orderId: UUID,
        newTableId: UUID,
        updatedAt: String
    ): Int

    @Query("UPDATE orders SET total_price = :totalPrice, updated_at = :updatedAt WHERE id = :orderId")
    suspend fun updateOrderTotal(orderId: UUID, totalPrice: Int, updatedAt: String)

    @Query("SELECT * FROM order_items WHERE order_id = :orderId AND product_id = :dishId AND deleted_at IS NULL LIMIT 1")
    suspend fun getItemForOrderAndDish(orderId: UUID, dishId: UUID): OrderItemEntity?

    @Query("SELECT * FROM order_items WHERE order_id = :orderId AND deleted_at IS NULL")
    suspend fun getItemsForOrderOnce(orderId: UUID): List<OrderItemEntity>

    @Query("UPDATE order_items SET quantity = :quantity, updated_at = :updatedAt WHERE id = :itemId")
    suspend fun updateOrderItemQuantity(itemId: UUID, quantity: Int, updatedAt: String)

    @Query("DELETE FROM order_items WHERE id = :itemId")
    suspend fun deleteOrderItem(itemId: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>)
}
