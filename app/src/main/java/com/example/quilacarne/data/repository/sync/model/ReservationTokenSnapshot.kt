package com.example.quilacarne.data.repository.sync.model

import java.util.UUID

internal data class ReservationTokenSnapshot(
    val reservationId: UUID,
    val reservationToken: String,
    val userToken: String?
)
