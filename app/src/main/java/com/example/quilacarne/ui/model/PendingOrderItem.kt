package com.example.quilacarne.ui.model

import com.example.quilacarne.data.local.entities.DishEntity
import java.util.UUID

data class PendingOrderItem(
    val sourceItemId: UUID?,
    val dishId: UUID?,
    val dish: DishEntity?,
    val quantity: Int,
    val priceAtTimeOfOrder: Int,
    val note: String? = null
)
