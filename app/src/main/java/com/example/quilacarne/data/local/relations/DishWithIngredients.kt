package com.example.quilacarne.data.local.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.IngredientEntity
import com.example.quilacarne.data.local.entities.DishCompositionEntity

data class DishWithIngredients(
    @Embedded val dish: DishEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id", // Zostawiamy 'id' tutaj, bo to ID składnika w tabeli ingredients
        associateBy = Junction(
            value = DishCompositionEntity::class,
            parentColumn = "dish_id",       // Kolumna w tabeli X łącząca z Dish
            entityColumn = "ingredient_id"  // Kolumna w tabeli X łącząca z Ingredient
        )
    )
    val ingredients: List<IngredientEntity>
)