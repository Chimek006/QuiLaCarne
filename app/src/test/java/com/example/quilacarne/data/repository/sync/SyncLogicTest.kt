package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.isActiveForTable
import com.example.quilacarne.data.local.entities.isOccupiedForTable
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.example.quilacarne.data.repository.sync.logic.ApiResponseLogic
import com.example.quilacarne.data.repository.sync.logic.PendingTableStatusLogic
import com.example.quilacarne.data.repository.sync.logic.ReservationAssignmentLogic
import com.example.quilacarne.data.repository.sync.logic.ReservationSelectionLogic
import com.example.quilacarne.data.repository.sync.logic.SyncForeignKeyGuard
import com.example.quilacarne.data.repository.sync.logic.TableDisplayStatusLogic
import com.example.quilacarne.data.repository.sync.logic.TableStatusSyncLogic
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
    fun resolveSyncedTableStatusUsesRemoteAvailableOverNewerLocalCleaning() {
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
    fun displayStatusUsesRemoteOccupiedAsSourceOfTruth() {
        assertEquals(
            "OCCUPIED",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "OCCUPIED",
                hasActiveOrder = false,
                hasReservation = true,
                previousStatusToken = null,
                isSyncing = false
            )
        )
        assertEquals(
            "OCCUPIED",
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
    fun displayStatusLetsRemoteAvailableOverrideAssignedOrder() {
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
    fun displayStatusLetsRemoteAvailableOverrideInProgressReservation() {
        assertEquals(
            "RESERVED",
            TableDisplayStatusLogic.resolveDisplayStatusToken(
                physicalStatusToken = "AVAILABLE",
                hasActiveOrder = false,
                hasReservation = true,
                hasInProgressReservation = true,
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
    fun orderIsOccupiedForTableOnlyWhenActiveAndWaiterIsAssigned() {
        assertEquals(true, order(statusTokens = "PENDING", waiterId = UUID.randomUUID()).isOccupiedForTable())
        assertEquals(false, order(statusTokens = "IN_PROGRESS", waiterId = null).isOccupiedForTable())
        assertEquals(false, order(statusTokens = "PENDING", waiterId = null).isOccupiedForTable())
        assertEquals(false, order(statusTokens = "COMPLETED", waiterId = UUID.randomUUID()).isOccupiedForTable())
    }

    @Test
    fun assignmentLogicTreatsAlreadyAssignedErrorAsSuccessOnlyAfterStateIsApplied() {
        assertEquals(
            true,
            ReservationAssignmentLogic.shouldTreatAssignFailureAsSuccess(
                errorMessage = "A waiter has already been assigned (reservation is in progress).",
                order = order(statusTokens = "PENDING", waiterId = null),
                reservation = reservation(statusTokens = "IN_PROGRESS")
            )
        )
        assertEquals(
            true,
            ReservationAssignmentLogic.shouldTreatAssignFailureAsSuccess(
                errorMessage = "reservation is in progress",
                order = order(statusTokens = "IN_PROGRESS", waiterId = null),
                reservation = reservation(statusTokens = "PENDING")
            )
        )
        assertEquals(
            false,
            ReservationAssignmentLogic.shouldTreatAssignFailureAsSuccess(
                errorMessage = "A waiter has already been assigned",
                order = order(statusTokens = "PENDING", waiterId = null),
                reservation = reservation(statusTokens = "PENDING")
            )
        )
        assertEquals(
            true,
            ReservationAssignmentLogic.shouldTreatAssignFailureAsSuccess(
                errorMessage = "Order not found",
                order = null,
                reservation = reservation(statusTokens = "IN_PROGRESS")
            )
        )
        assertEquals(
            false,
            ReservationAssignmentLogic.shouldTreatAssignFailureAsSuccess(
                errorMessage = "Order not found",
                order = null,
                reservation = reservation(statusTokens = "ACTIVE")
            )
        )
    }

    @Test
    fun assignmentLogicSeesAssignedWaiterAsAppliedState() {
        assertEquals(
            true,
            ReservationAssignmentLogic.isAssignmentApplied(
                order = order(statusTokens = "PENDING", waiterId = UUID.randomUUID()),
                reservation = reservation(statusTokens = "PENDING")
            )
        )
    }

    @Test
    fun pendingTableStatusLogicMapsOptimisticActionsToDisplayStatusTokens() {
        assertEquals(
            "CLEANING",
            PendingTableStatusLogic.statusTokenForAction(PendingRequestRepository.ACTION_MARK_CLEANING)
        )
        assertEquals(
            "AVAILABLE",
            PendingTableStatusLogic.statusTokenForAction(PendingRequestRepository.ACTION_MARK_AVAILABLE)
        )
        assertEquals(
            "OUT_OF_SERVICE",
            PendingTableStatusLogic.statusTokenForAction(PendingRequestRepository.ACTION_MARK_OUT_OF_SERVICE)
        )
        assertNull(PendingTableStatusLogic.statusTokenForAction("UNKNOWN"))
    }

    @Test
    fun foreignKeyGuardKeepsOnlyReservationsWithKnownTables() {
        val knownTableId = UUID.randomUUID()
        val missingTableId = UUID.randomUUID()
        val kept = reservation(statusTokens = "ACTIVE").copy(tableId = knownTableId)
        val skipped = reservation(statusTokens = "ACTIVE").copy(tableId = missingTableId)

        val result = SyncForeignKeyGuard.filterReservationsByKnownTables(
            reservations = listOf(kept, skipped),
            knownTableIds = setOf(knownTableId)
        )

        assertEquals(listOf(kept.id), result.map { it.id })
    }

    @Test
    fun foreignKeyGuardDropsOrdersWithoutKnownTablesAndClearsMissingWaiters() {
        val knownTableId = UUID.randomUUID()
        val knownWaiterId = UUID.randomUUID()
        val missingWaiterId = UUID.randomUUID()
        val orderWithKnownWaiter = order("PENDING", knownWaiterId).copy(tableId = knownTableId)
        val orderWithMissingWaiter = order("PENDING", missingWaiterId).copy(tableId = knownTableId)
        val orderWithMissingTable = order("PENDING", knownWaiterId).copy(tableId = UUID.randomUUID())

        val result = SyncForeignKeyGuard.sanitizeOrders(
            orders = listOf(orderWithKnownWaiter, orderWithMissingWaiter, orderWithMissingTable),
            knownTableIds = setOf(knownTableId),
            knownUserIds = setOf(knownWaiterId)
        )

        assertEquals(listOf(orderWithKnownWaiter.id, orderWithMissingWaiter.id), result.map { it.id })
        assertEquals(knownWaiterId, result[0].waiterId)
        assertNull(result[1].waiterId)
    }

    @Test
    fun foreignKeyGuardDropsOrderItemsWithoutKnownOrdersOrDishes() {
        val knownOrderId = UUID.randomUUID()
        val knownDishId = UUID.randomUUID()
        val kept = orderItem(orderId = knownOrderId, dishId = knownDishId)
        val missingOrder = orderItem(orderId = UUID.randomUUID(), dishId = knownDishId)
        val missingDish = orderItem(orderId = knownOrderId, dishId = UUID.randomUUID())

        val result = SyncForeignKeyGuard.filterOrderItems(
            items = listOf(kept, missingOrder, missingDish),
            knownOrderIds = setOf(knownOrderId),
            knownDishIds = setOf(knownDishId)
        )

        assertEquals(listOf(kept.id), result.map { it.id })
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

    private fun order(
        statusTokens: String,
        waiterId: UUID? = UUID.randomUUID()
    ): OrderEntity {
        return OrderEntity(
            id = UUID.randomUUID(),
            tableId = UUID.randomUUID(),
            waiterId = waiterId,
            statusId = null,
            statusTokens = statusTokens,
            createdAt = "created",
            updatedAt = "updated"
        )
    }

    private fun reservation(statusTokens: String): ReservationEntity {
        val id = UUID.randomUUID()
        return ReservationEntity(
            id = id,
            token = "reservation-$id",
            tableId = UUID.randomUUID(),
            tableToken = "table",
            userToken = "user",
            startTime = "start",
            endTime = "end",
            startEpochMillis = 1_000L,
            endEpochMillis = 2_000L,
            statusTokens = statusTokens,
            isActive = true,
            createdAt = "created",
            updatedAt = "updated"
        )
    }

    private fun orderItem(
        orderId: UUID,
        dishId: UUID?
    ): OrderItemEntity {
        return OrderItemEntity(
            id = UUID.randomUUID(),
            orderId = orderId,
            productId = dishId,
            quantity = 1,
            priceAtTimeOfOrder = 100,
            createdAt = "created",
            updatedAt = "updated"
        )
    }
}
