package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import androidx.room.ForeignKey
import java.util.Locale

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(entity = RestaurantTableEntity::class, parentColumns = ["id"], childColumns = ["table_id"]),
        ForeignKey(entity = UsersEntity::class, parentColumns = ["id"], childColumns = ["waiter_id"]),
        ForeignKey(entity = OrderStatusEntity::class, parentColumns = ["id"], childColumns = ["status_id"])
    ]
)
data class OrderEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "table_id") val tableId: UUID?,
    @ColumnInfo(name = "waiter_id") val waiterId: UUID?,
    @ColumnInfo(name = "status_id") val statusId: UUID?,
    @ColumnInfo(name = "status_tokens") val statusTokens: String = "",
    @ColumnInfo(name = "total_price") val totalPrice: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)

fun OrderEntity.isActiveForTable(): Boolean {
    val tokens = statusTokens
        .split(',')
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotBlank() }
        .toSet()

    val inactiveTokens = setOf(
        "CANCELLED",
        "CANCELED",
        "COMPLETED",
        "DONE",
        "FINISHED",
        "PAID",
        "CLOSED",
        "NO_SHOW"
    )
    val activeTokens = setOf("IN_PROGRESS", "PENDING")

    return tokens.isNotEmpty() &&
        tokens.none { it in inactiveTokens } &&
        tokens.any { it in activeTokens }
}
