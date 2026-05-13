package com.example.quilacarne.data.local.dao

import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReservationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var tableId: UUID

    @Before
    fun setUp() = runTest {
        db = TestDatabaseFactory.create()
        tableId = UUID.randomUUID()
        val statusId = UUID.randomUUID()
        db.tableStatusDao().insertAll(
            listOf(
                TableStatusEntity(
                    id = statusId,
                    token = "AVAILABLE",
                    namePl = "Wolny",
                    nameEn = "Available",
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
        db.restaurantTableDao().insertTables(
            listOf(
                RestaurantTableEntity(
                    id = tableId,
                    tableNumber = 1,
                    capacity = 4,
                    statusId = statusId,
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAllAndGetReservationsForTableFlowReturnStoredReservations() = runTest {
        val reservation = reservation(startMillis = 1_000L, endMillis = 2_000L)

        db.reservationDao().insertAll(listOf(reservation))

        assertEquals(listOf(reservation), db.reservationDao().getReservationsForTableFlow(tableId).first())
    }

    @Test
    fun currentAndUpcomingQueriesReturnMatchingReservation() = runTest {
        val current = reservation(startMillis = 1_000L, endMillis = 2_000L)
        val upcoming = reservation(startMillis = 3_000L, endMillis = 4_000L)
        val inactive = reservation(startMillis = 1_000L, endMillis = 5_000L, isActive = false)
        db.reservationDao().insertAll(listOf(inactive, upcoming, current))

        assertEquals(current.id, db.reservationDao().getCurrentReservationForTable(tableId, 1_500L)?.id)
        assertEquals(upcoming.id, db.reservationDao().getUpcomingReservationForTable(tableId, 1_500L)?.id)
    }

    @Test
    fun deleteReservationsExceptKeepsOnlyProvidedIds() = runTest {
        val kept = reservation(startMillis = 1_000L, endMillis = 2_000L)
        val removed = reservation(startMillis = 3_000L, endMillis = 4_000L)
        db.reservationDao().insertAll(listOf(kept, removed))

        db.reservationDao().deleteReservationsExcept(listOf(kept.id))

        assertEquals(listOf(kept), db.reservationDao().getReservationsFlow().first())
    }

    @Test
    fun clearAllRemovesReservations() = runTest {
        db.reservationDao().insertAll(listOf(reservation(startMillis = 1_000L, endMillis = 2_000L)))

        db.reservationDao().clearAll()

        assertEquals(emptyList<ReservationEntity>(), db.reservationDao().getReservationsFlow().first())
        assertNull(db.reservationDao().getCurrentReservationForTable(tableId, 1_500L))
    }

    private fun reservation(
        startMillis: Long,
        endMillis: Long,
        isActive: Boolean = true
    ): ReservationEntity {
        val id = UUID.randomUUID()
        return ReservationEntity(
            id = id,
            token = "reservation-$id",
            tableId = tableId,
            tableToken = "table-token",
            userToken = "user-token",
            startTime = "start",
            endTime = "end",
            startEpochMillis = startMillis,
            endEpochMillis = endMillis,
            statusTokens = "ACTIVE",
            isActive = isActive,
            createdAt = "created",
            updatedAt = "updated"
        )
    }
}
