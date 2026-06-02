package com.example.quilacarne.data.repository.sync.logic

import com.example.quilacarne.data.repository.sync.model.OccupyReservationSelection

internal object ReservationSelectionLogic {
    fun chooseReservationForOccupy(
        currentToken: String?,
        upcomingToken: String?
    ): OccupyReservationSelection? {
        return when {
            !currentToken.isNullOrBlank() -> OccupyReservationSelection(
                token = currentToken,
                type = "current"
            )
            !upcomingToken.isNullOrBlank() -> OccupyReservationSelection(
                token = upcomingToken,
                type = "upcoming"
            )
            else -> null
        }
    }
}
