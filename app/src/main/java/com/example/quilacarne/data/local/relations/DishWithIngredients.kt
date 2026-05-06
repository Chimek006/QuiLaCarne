package com.example.quilacarne.data.local.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.quilacarne.data.local.entities.*

data class IngredientWithAllergens(
    @Embedded val ingredient: IngredientEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = IngredientAllergenEntity::class,
            parentColumn = "ingredient_id",
            entityColumn = "allergen_id"
        )
    )
    val allergens: List<AllergenEntity>
)

data class DishWithIngredients(
    @Embedded val dish: DishEntity,
    @Relation(
        entity = IngredientEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DishCompositionEntity::class,
            parentColumn = "dish_id",
            entityColumn = "ingredient_id"
        )
    )
    val ingredientsWithAllergens: List<IngredientWithAllergens>
)