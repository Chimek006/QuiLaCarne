package com.example.quilacarne.ui.state

import java.util.UUID

data class TableUiState(
    val id: UUID,
    val tableNumber: Int,
    val statusToken: String,
    val statusNamePl: String,
    val statusNameEn: String,
    val waiterName: String?,
    val reservationTime: String?
)
