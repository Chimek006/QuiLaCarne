package com.example.quilacarne.data.repository.sync.model

internal data class OperationalSyncSnapshot(
    val tables: TablesSyncSnapshot,
    val users: UsersSyncSnapshot,
    val reservations: ReservationsSyncSnapshot,
    val ordersAndItems: OrdersAndItemsSyncSnapshot
)
