package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import androidx.room.ForeignKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["order_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DishEntity::class, parentColumns = ["id"], childColumns = ["product_id"])
    ]
)
data class OrderItemEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "order_id") val orderId: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID?,
    val quantity: Int,
    @ColumnInfo(name = "price_at_time_of_order") val priceAtTimeOfOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)