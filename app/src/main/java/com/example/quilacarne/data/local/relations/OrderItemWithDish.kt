package com.example.quilacarne.data.local.relations // lub Twój pakiet entities

import androidx.room.Embedded
import androidx.room.Relation
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.DishEntity

data class OrderItemWithDish(
    @Embedded val item: OrderItemEntity,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "id"
    )
    val dish: DishEntity?
)