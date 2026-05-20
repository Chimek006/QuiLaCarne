package com.example.quilacarne.data.remote.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealtimeEventParserTest {
    @Test
    fun parseReadsTypeAndPayload() {
        val event = RealtimeEventParser.parse(
            """{"type":"table_status_changed","payload":{"tableNumber":2}}"""
        )

        assertEquals("TABLE_STATUS_CHANGED", event?.type)
        assertEquals("2", event?.payload?.stringOrNull("tableNumber"))
    }

    @Test
    fun parseAcceptsEventFieldWhenTypeIsMissing() {
        val event = RealtimeEventParser.parse(
            """{"event":"WAITER_ASSIGNED","payload":{"waiterUsername":"anna"}}"""
        )

        assertEquals("WAITER_ASSIGNED", event?.type)
        assertEquals("anna", event?.payload?.stringOrNull("waiterUsername"))
    }

    @Test
    fun parseReturnsNullForInvalidOrUntypedMessages() {
        assertNull(RealtimeEventParser.parse("not-json"))
        assertNull(RealtimeEventParser.parse("""{"payload":{"tableNumber":2}}"""))
    }
}
