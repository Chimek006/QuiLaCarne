package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import androidx.room.ForeignKey

@Entity(
    tableName = "x_dish_composition",
    primaryKeys = ["dish_id", "ingredient_id"],
    foreignKeys = [
        ForeignKey(entity = DishEntity::class, parentColumns = ["id"], childColumns = ["dish_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredient_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class DishCompositionEntity(
    @ColumnInfo(name = "dish_id") val dishId: UUID,
    @ColumnInfo(name = "ingredient_id") val ingredientId: UUID,
    val quantity: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)