package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import androidx.room.ForeignKey

@Entity(
    tableName = "x_ingredient_allergens",
    primaryKeys = ["ingredient_id", "allergen_id"],
    foreignKeys = [
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredient_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AllergenEntity::class, parentColumns = ["id"], childColumns = ["allergen_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class IngredientAllergenEntity(
    @ColumnInfo(name = "ingredient_id") val ingredientId: UUID,
    @ColumnInfo(name = "allergen_id") val allergenId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)