package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.isActiveForTable
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class SyncLogicTest {
    @Test
    fun chooseReservationForOccupyUsesCurrentBeforeUpcoming() {
        val selection = ReservationSelectionLogic.chooseReservationForOccupy(
            currentToken = "current-token",
            upcomingToken = "upcoming-token"
        )

        assertEquals("current-token", selection?.token)
        assertEquals("current", selection?.type)
    }

    @Test
    fun chooseReservationForOccupyFallsBackToUpcoming() {
        val selection = ReservationSelectionLogic.chooseReservationForOccupy(
            currentToken = null,
            upcomingToken = "upcoming-token"
        )

        assertEquals("upcoming-token", selection?.token)
        assertEquals("upcoming", selection?.type)
    }

    @Test
    fun chooseReservationForOccupyReturnsNullWhenNoReservationExists() {
        assertNull(
            ReservationSelectionLogic.chooseReservationForOccupy(
                currentToken = null,
                upcomingToken = null
            )
        )
    }

    @Test
    fun chooseStatusTokenUsesTableStatusPriority() {
        assertEquals(
            "OUT_OF_SERVICE",
            TableStatusSyncLogic.chooseStatusToken(
                listOf("AVAILABLE", "OCCUPIED", "OUT_OF_SERVICE")
            )
        )
        assertEquals(
            "CLEANING",
            TableStatusSyncLogic.chooseStatusToken(listOf("AVAILABLE", "CLEANING"))
        )
        assertEquals(
            "AVAILABLE",
            TableStatusSyncLogic.chooseStatusToken(listOf("OCCUPIED", "AVAILABLE"))
        )
        assertEquals(
            "CUSTOM",
            TableStatusSyncLogic.chooseStatusToken(listOf("", "CUSTOM"))
        )
        assertNull(TableStatusSyncLogic.chooseStatusToken(emptyList()))
    }

    @Test
    fun resolveSyncedTableStatusUsesRemoteAvailableOverLocalBusyState() {
        assertEquals(
            "AVAILABLE",
            TableStatusSyncLogic.resolveSyncedTableStatusToken(
                remoteStatusToken = "AVAILABLE",
                existingStatusToken = "CLEANING",
                remoteUpdatedAt = "2024-01-01T10:00:00Z",
                existingUpdatedAt = "2024-01-01T10:05:00Z"
            )
        )
    }

    @Test
    fun resolveSyncedTableStatusKeepsLocalStatusWhenRemoteStatusIsEmpty() {
        assertEquals(
            "OUT_OF_SERVICE",
            TableStatusSyncLogic.resolveSyncedTableStatusToken(
                remoteStatusToken = "",
                existingStatusToken = "OUT_OF_SERVICE",
                remoteUpdatedAt = "2024-01-01T10:10:00Z",
                existingUpdatedAt = "2024-01-01T10:05:00Z"
            )
        )
        assertEquals(
            "OCCUPIED",
            TableStatusSyncLogic.resolveSyncedTableStatusToken(
                remoteStatusToken = null,
                existingStatusToken = "OCCUPIED",
                remoteUpdatedAt = null,
                existingUpdatedAt = null
            )
        )
    }

    @Test
    fun resolveSyncedTableStatusAllowsNewerRemoteAvailable() {
        assertEquals(
            "AVAILABLE",
            TableStatusSyncLogic.resolveSyncedTableStatusToken(
                remoteStatusToken = "AVAILABLE",
                existingStatusToken = "OUT_OF_SERVICE",
                remoteUpdatedAt = "2024-01-01T10:10:00Z",
                existingUpdatedAt = "2024-01-01T10:05:00Z"
            )
        )
    }

    @Test
    fun resolveSyncedTableStatusUsesRemoteNonAvailableStatusImmediately() {
        assertEquals(
            "OCCUPIED",
            TableStatusSyncLogic.resolveSyncedTableStatusToken(
                remoteStatusToken = "occupied",
                existingStatusToken = "AVAILABLE",
                remoteUpdatedAt = null,
                existingUpdatedAt = null
            )
        )
    }

    @Test
    fun displayStatusKeepsPhysicalStatusesAboveReservationAndOrders() {
        assertEquals(
            "OUT_OF_SERVICE",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "OUT_OF_SERVICE",
                hasActiveOrder = true,
                hasReservation = true,
                previousStatusToken = "RESERVED",
                isSyncing = false
            )
        )
        assertEquals(
            "CLEANING",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "CLEANING",
                hasActiveOrder = true,
                hasReservation = true,
                previousStatusToken = "OCCUPIED",
                isSyncing = false
            )
        )
    }

    @Test
    fun displayStatusDoesNotUseLocalOccupiedWithoutActiveOrder() {
        assertEquals(
            "RESERVED",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "OCCUPIED",
                hasActiveOrder = false,
                hasReservation = true,
                previousStatusToken = null,
                isSyncing = false
            )
        )
        assertEquals(
            "AVAILABLE",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "OCCUPIED",
                hasActiveOrder = false,
                hasReservation = false,
                previousStatusToken = null,
                isSyncing = false
            )
        )
    }

    @Test
    fun displayStatusTrustsRemoteAvailableOverStaleActiveOrder() {
        assertEquals(
            "RESERVED",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "AVAILABLE",
                hasActiveOrder = true,
                hasReservation = true,
                previousStatusToken = null,
                isSyncing = false
            )
        )
        assertEquals(
            "AVAILABLE",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "AVAILABLE",
                hasActiveOrder = true,
                hasReservation = false,
                previousStatusToken = null,
                isSyncing = false
            )
        )
        assertEquals(
            "RESERVED",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "AVAILABLE",
                hasActiveOrder = false,
                hasReservation = true,
                previousStatusToken = null,
                isSyncing = false
            )
        )
    }

    @Test
    fun displayStatusUsesActiveOrderWhenRemotePhysicalStatusIsUnknown() {
        assertEquals(
            "OCCUPIED",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = null,
                hasActiveOrder = true,
                hasReservation = false,
                previousStatusToken = null,
                isSyncing = false
            )
        )
    }

    @Test
    fun displayStatusDoesNotKeepPreviousBusyStatusDuringSyncWhenRawStateLooksAvailable() {
        assertEquals(
            "AVAILABLE",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "AVAILABLE",
                hasActiveOrder = false,
                hasReservation = false,
                previousStatusToken = "RESERVED",
                isSyncing = true
            )
        )
    }

    @Test
    fun orderIsActiveForTableOnlyForPendingOrInProgressStatuses() {
        assertEquals(true, order(statusTokens = "IN_PROGRESS").isActiveForTable())
        assertEquals(true, order(statusTokens = "pending").isActiveForTable())
        assertEquals(false, order(statusTokens = "").isActiveForTable())
        assertEquals(false, order(statusTokens = "COMPLETED").isActiveForTable())
        assertEquals(false, order(statusTokens = "PENDING,CANCELLED").isActiveForTable())
        assertEquals(false, order(statusTokens = "PAID").isActiveForTable())
    }

    @Test
    fun displayStatusAllowsAvailableAfterSyncCompletes() {
        assertEquals(
            "AVAILABLE",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "AVAILABLE",
                hasActiveOrder = false,
                hasReservation = false,
                previousStatusToken = "CLEANING",
                isSyncing = false
            )
        )
    }

    @Test
    fun tableStatusNamesMapKnownTokensAndHumanizeUnknownTokens() {
        assertEquals("Wolny", TableStatusSyncLogic.tableStatusNamePl("AVAILABLE"))
        assertEquals("Available", TableStatusSyncLogic.tableStatusNameEn("AVAILABLE"))
        assertEquals("Do sprzatniecia", TableStatusSyncLogic.tableStatusNamePl("CLEANING"))
        assertEquals("Out of service", TableStatusSyncLogic.tableStatusNameEn("OUT_OF_SERVICE"))
        assertEquals("Custom status", TableStatusSyncLogic.tableStatusNameEn("CUSTOM_STATUS"))
    }

    @Test
    fun errorMessageFromBodyExtractsMessageFromJsonOrReturnsRawText() {
        assertEquals(
            "Wolny stolik nie wymaga sprzatania",
            ApiResponseLogic.errorMessageFromBody(
                """{"data":null,"message":"Wolny stolik nie wymaga sprzatania","statusCode":400,"errorMessages":[],"success":false}"""
            )
        )
        assertEquals("plain error", ApiResponseLogic.errorMessageFromBody("plain error"))
        assertNull(ApiResponseLogic.errorMessageFromBody(""))
    }

    @Test
    fun assertWrapperSuccessAcceptsSuccessfulWrappers() {
        ApiResponseLogic.assertWrapperSuccess(
            ApiResponse<Unit>(isSuccess = true, statusCode = 400),
            fallbackMessage = "fallback"
        )
        ApiResponseLogic.assertWrapperSuccess(
            ApiResponse<Unit>(isSuccess = false, statusCode = 204),
            fallbackMessage = "fallback"
        )
        ApiResponseLogic.assertWrapperSuccess(
            ApiResponse<Unit>(isSuccess = false, statusCode = 0, errorMessages = emptyList()),
            fallbackMessage = "fallback"
        )
    }

    @Test
    fun assertWrapperSuccessThrowsUsefulMessageForFailedWrappers() {
        val exception = assertThrows(Exception::class.java) {
            ApiResponseLogic.assertWrapperSuccess(
                ApiResponse<Unit>(
                    isSuccess = false,
                    statusCode = 400,
                    message = "Bad state",
                    errorMessages = listOf("Ignored")
                ),
                fallbackMessage = "fallback"
            )
        }

        assertEquals("Bad state", exception.message)
    }

    private fun order(statusTokens: String): OrderEntity {
        return OrderEntity(
            id = UUID.randomUUID(),
            tableId = UUID.randomUUID(),
            waiterId = UUID.randomUUID(),
            statusId = null,
            statusTokens = statusTokens,
            createdAt = "created",
            updatedAt = "updated"
        )
    }
}
