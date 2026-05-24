package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.ReservationEntity
import java.util.UUID

internal data class ReservationsSyncSnapshot(
    val reservations: List<ReservationEntity> = emptyList(),
    val reservationTokens: List<ReservationTokenSnapshot> = emptyList(),
    val currentReservationTokensByTableId: Map<UUID, String> = emptyMap()
)
