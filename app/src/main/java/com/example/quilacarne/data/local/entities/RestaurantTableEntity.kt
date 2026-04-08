package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import androidx.room.ForeignKey

@Entity(
    tableName = "restaurant_tables",
    foreignKeys = [
        ForeignKey(
            entity = TableStatusEntity::class,
            parentColumns = ["id"],
            childColumns = ["status_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RestaurantTableEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "table_number") val tableNumber: Int,
    val capacity: Int,
    @ColumnInfo(name = "status_id") val statusId: UUID?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)