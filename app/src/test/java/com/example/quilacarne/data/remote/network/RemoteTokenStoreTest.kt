package com.example.quilacarne.data.remote.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class RemoteTokenStoreTest {
    private lateinit var context: Context
    private lateinit var store: RemoteTokenStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("remote_token_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = RemoteTokenStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("remote_token_store", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun saveTokenAndGetTokenRoundTripByTypeAndLocalId() {
        val id = UUID.randomUUID()

        store.saveToken("table", id, "remote-table-token")

        assertEquals("remote-table-token", store.getToken("table", id))
        assertNull(store.getToken("reservation", id))
    }

    @Test
    fun saveTokenIgnoresNullAndBlankTokens() {
        val id = UUID.randomUUID()

        store.saveToken("table", id, null)
        store.saveToken("dish", id, "")
        store.saveToken("order", id, " ")

        assertNull(store.getToken("table", id))
        assertNull(store.getToken("dish", id))
        assertNull(store.getToken("order", id))
    }

    @Test
    fun orderReservationTokenRoundTripIgnoresBlank() {
        val orderId = UUID.randomUUID()

        store.saveOrderReservationToken(orderId, "")
        assertNull(store.getOrderReservationToken(orderId))

        store.saveOrderReservationToken(orderId, "reservation-token")
        assertEquals("reservation-token", store.getOrderReservationToken(orderId))
    }

    @Test
    fun tableReservationTokenCanBeCleared() {
        val tableId = UUID.randomUUID()

        store.saveTableReservationToken(tableId, "reservation-token")
        assertEquals("reservation-token", store.getTableReservationToken(tableId))

        store.clearTableReservationToken(tableId)

        assertNull(store.getTableReservationToken(tableId))
    }

    @Test
    fun reservationUserTokenRoundTripIgnoresBlank() {
        store.saveReservationUserToken("reservation-token", "")
        assertNull(store.getReservationUserToken("reservation-token"))

        store.saveReservationUserToken("reservation-token", "user-token")
        assertEquals("user-token", store.getReservationUserToken("reservation-token"))
    }
}
