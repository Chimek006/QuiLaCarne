package com.example.quilacarne.data.remote.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DishReadyEventLogicTest {
    @Test
    fun readyStatusesAreRecognizedCaseInsensitive() {
        listOf(
            "READY",
            "READY_FOR_PICKUP",
            "READY_TO_SERVE",
            "PREPARED",
            "DONE",
            "GOTOWE",
            "ready"
        ).forEach { status ->
            assertTrue("Expected $status to be ready", DishReadyEventLogic.isReadyStatus(status))
        }
    }

    @Test
    fun nonReadyStatusesAreIgnored() {
        listOf(
            "PENDING",
            "IN_PROGRESS",
            "CANCELLED",
            "",
            null
        ).forEach { status ->
            assertFalse("Expected $status to be non-ready", DishReadyEventLogic.isReadyStatus(status))
        }
    }

    @Test
    fun dishReadyEventDoesNotRequireStatusToken() {
        val event = RealtimeEventParser.parse(
            """{"type":"DISH_READY","payload":{"dishName":"Pizza"}}"""
        )

        assertTrue(DishReadyEventLogic.isReadyEvent(event!!))
    }

    @Test
    fun orderItemStatusChangedRequiresReadyStatus() {
        val ready = RealtimeEventParser.parse(
            """{"type":"ORDER_ITEM_STATUS_CHANGED","payload":{"status":"READY_FOR_PICKUP"}}"""
        )
        val pending = RealtimeEventParser.parse(
            """{"type":"ORDER_ITEM_STATUS_CHANGED","payload":{"statusToken":"PENDING"}}"""
        )

        assertTrue(DishReadyEventLogic.isReadyEvent(ready!!))
        assertFalse(DishReadyEventLogic.isReadyEvent(pending!!))
    }
}
