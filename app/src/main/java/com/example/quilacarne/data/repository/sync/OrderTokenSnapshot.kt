package com.example.quilacarne.data.repository.sync

import java.util.UUID

internal data class OrderTokenSnapshot(
    val orderId: UUID,
    val orderToken: String,
    val reservationToken: String?
)
