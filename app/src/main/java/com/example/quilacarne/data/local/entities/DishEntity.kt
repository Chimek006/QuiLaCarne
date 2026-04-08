package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import androidx.room.ForeignKey

@Entity(
    tableName = "dishes",
    foreignKeys = [
        ForeignKey(
            entity = DishCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class DishEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "category_id") val categoryId: UUID?,
    val name: String,
    val price: Int,
    @ColumnInfo(name = "is_available") val isAvailable: Boolean = true,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)