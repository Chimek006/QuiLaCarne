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
    fun parseAcceptsNameFieldWhenTypeAndEventAreMissing() {
        val event = RealtimeEventParser.parse(
            """{"name":"DISH_READY","payload":{"productName":"Pierogi","tableNo":7}}"""
        )

        assertEquals("DISH_READY", event?.type)
        assertEquals("Pierogi", DishReadyEventLogic.dishName(event?.payload!!))
        assertEquals("7", DishReadyEventLogic.tableNumber(event.payload!!))
    }

    @Test
    fun parseUsesRootAsPayloadWhenPayloadIsMissing() {
        val event = RealtimeEventParser.parse(
            """{"type":"DISH_READY","dishName":"Pizza Margherita","tableNumber":4}"""
        )

        assertEquals("DISH_READY", event?.type)
        assertEquals("Pizza Margherita", DishReadyEventLogic.dishName(event?.payload!!))
        assertEquals("4", DishReadyEventLogic.tableNumber(event.payload!!))
    }

    @Test
    fun parseOrderItemStatusChangedWithAlternativeFieldNames() {
        val event = RealtimeEventParser.parse(
            """
            {
              "event":"ORDER_ITEM_STATUS_CHANGED",
              "payload":{
                "itemToken":"item-1",
                "order":"order-1",
                "itemName":"Soup",
                "table":3,
                "orderItemStatus":"prepared"
              }
            }
            """.trimIndent()
        )

        assertEquals("ORDER_ITEM_STATUS_CHANGED", event?.type)
        assertEquals(true, DishReadyEventLogic.isReadyEvent(event!!))
        assertEquals("Soup", DishReadyEventLogic.dishName(event.payload!!))
        assertEquals("3", DishReadyEventLogic.tableNumber(event.payload!!))
    }

    @Test
    fun parseReturnsNullForInvalidOrUntypedMessages() {
        assertNull(RealtimeEventParser.parse("not-json"))
        assertNull(RealtimeEventParser.parse("""{"payload":{"tableNumber":2}}"""))
    }
}
