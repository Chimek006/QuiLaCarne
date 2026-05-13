package com.example.quilacarne.utils

import com.example.quilacarne.data.local.entities.ReservationEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

class ReservationTimeUtilsTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun parseApiTimestampMillis_handlesSecondsAndMillisFormats() {
        assertEquals(
            1_704_105_000_000L,
            ReservationTimeUtils.parseApiTimestampMillis("2024-01-01T10:30:00Z")
        )
        assertEquals(
            1_704_105_000_123L,
            ReservationTimeUtils.parseApiTimestampMillis("2024-01-01T10:30:00.123Z")
        )
        assertNull(ReservationTimeUtils.parseApiTimestampMillis("not-a-date"))
    }

    @Test
    fun isActiveStatus_rejectsInactiveReservationStatuses() {
        assertTrue(ReservationTimeUtils.isActiveStatus(listOf("ACTIVE")))
        assertTrue(ReservationTimeUtils.isActiveStatus(listOf("active", "VIP")))
        assertFalse(ReservationTimeUtils.isActiveStatus(listOf("ACTIVE", "CANCELLED")))
        assertFalse(ReservationTimeUtils.isActiveStatus(listOf("NO_SHOW")))
        assertFalse(ReservationTimeUtils.isActiveStatus(listOf("ABSENT")))
    }

    @Test
    fun currentAndUpcomingRespectStartEndBounds() {
        val current = reservation(
            startMillis = 1_000L,
            endMillis = 2_000L,
            isActive = true
        )
        val upcoming = reservation(
            startMillis = 3_000L,
            endMillis = 4_000L,
            isActive = true
        )
        val inactive = reservation(
            startMillis = 1_000L,
            endMillis = 2_000L,
            isActive = false
        )

        assertTrue(ReservationTimeUtils.isCurrent(current, nowMillis = 1_000L))
        assertTrue(ReservationTimeUtils.isCurrent(current, nowMillis = 1_999L))
        assertFalse(ReservationTimeUtils.isCurrent(current, nowMillis = 2_000L))
        assertFalse(ReservationTimeUtils.isCurrent(inactive, nowMillis = 1_500L))

        assertTrue(ReservationTimeUtils.isUpcoming(upcoming, nowMillis = 2_999L))
        assertFalse(ReservationTimeUtils.isUpcoming(upcoming, nowMillis = 3_000L))
    }

    @Test
    fun selectCurrentOrUpcomingPrefersCurrentThenNearestUpcoming() {
        val laterUpcoming = reservation(startMillis = 5_000L, endMillis = 6_000L)
        val nearestUpcoming = reservation(startMillis = 3_000L, endMillis = 4_000L)
        val current = reservation(startMillis = 1_000L, endMillis = 2_000L)

        assertEquals(
            current.id,
            ReservationTimeUtils.selectCurrentOrUpcoming(
                listOf(laterUpcoming, nearestUpcoming, current),
                nowMillis = 1_500L
            )?.id
        )
        assertEquals(
            nearestUpcoming.id,
            ReservationTimeUtils.selectCurrentOrUpcoming(
                listOf(laterUpcoming, nearestUpcoming, current),
                nowMillis = 2_500L
            )?.id
        )
        assertNull(
            ReservationTimeUtils.selectCurrentOrUpcoming(
                listOf(current),
                nowMillis = 2_500L
            )
        )
    }

    @Test
    fun formatTimeRangeReturnsLocalHourRange() {
        assertEquals(
            "10:30 - 11:30",
            ReservationTimeUtils.formatTimeRange(
                "2024-01-01T10:30:00Z",
                "2024-01-01T11:30:00Z"
            )
        )
    }

    @Test
    fun statusTokensToTextJoinsWithComma() {
        assertEquals("ACTIVE,VIP", ReservationTimeUtils.statusTokensToText(listOf("ACTIVE", "VIP")))
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
            tableId = UUID.randomUUID(),
            tableToken = "table",
            userToken = "user",
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
