package com.example.quilacarne.data.remote.network

import android.content.Context
import java.util.UUID

class RemoteTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("remote_token_store", Context.MODE_PRIVATE)

    fun saveToken(type: String, localId: UUID, token: String?) {
        if (token.isNullOrBlank()) return
        prefs.edit()
            .putString(tokenKey(type, localId), token)
            .apply()
    }

    fun getToken(type: String, localId: UUID): String? {
        return prefs.getString(tokenKey(type, localId), null)
    }

    fun saveOrderReservationToken(orderId: UUID, reservationToken: String?) {
        if (reservationToken.isNullOrBlank()) return
        prefs.edit()
            .putString("order_reservation_$orderId", reservationToken)
            .apply()
    }

    fun getOrderReservationToken(orderId: UUID): String? {
        return prefs.getString("order_reservation_$orderId", null)
    }

    fun saveTableReservationToken(tableId: UUID, reservationToken: String?) {
        if (reservationToken.isNullOrBlank()) return
        prefs.edit()
            .putString("table_reservation_$tableId", reservationToken)
            .apply()
    }

    fun getTableReservationToken(tableId: UUID): String? {
        return prefs.getString("table_reservation_$tableId", null)
    }

    fun clearTableReservationToken(tableId: UUID) {
        prefs.edit()
            .remove("table_reservation_$tableId")
            .apply()
    }

    fun saveReservationUserToken(reservationToken: String, userToken: String?) {
        if (userToken.isNullOrBlank()) return
        prefs.edit()
            .putString("reservation_user_$reservationToken", userToken)
            .apply()
    }

    fun getReservationUserToken(reservationToken: String): String? {
        return prefs.getString("reservation_user_$reservationToken", null)
    }

    private fun tokenKey(type: String, localId: UUID): String = "${type}_token_$localId"
}
