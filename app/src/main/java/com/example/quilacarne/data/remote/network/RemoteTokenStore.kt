package com.example.quilacarne.data.remote.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.quilacarne.utils.SecurePreferences
import java.util.Locale
import java.util.UUID

class RemoteTokenStore(context: Context) {
    private val prefs: SharedPreferences = SecurePreferences.encrypted(context, ENCRYPTED_PREFS_NAME)
    private val legacyPrefs: SharedPreferences = SecurePreferences.legacy(context, LEGACY_PREFS_NAME)

    init {
        migrateLegacyPrefs()
    }

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

    fun saveLocalTableStatusOverride(tableId: UUID, statusToken: String?) {
        val normalizedToken = statusToken
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf { it.isNotBlank() }

        if (normalizedToken == null) {
            clearLocalTableStatusOverride(tableId)
            return
        }

        prefs.edit()
            .putString("table_status_override_$tableId", normalizedToken)
            .apply()
    }

    fun getLocalTableStatusOverride(tableId: UUID): String? {
        return prefs.getString("table_status_override_$tableId", null)
    }

    fun clearLocalTableStatusOverride(tableId: UUID) {
        prefs.edit()
            .remove("table_status_override_$tableId")
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

    fun saveReservationWaiterId(reservationToken: String, waiterId: UUID?) {
        if (reservationToken.isBlank() || waiterId == null) return
        prefs.edit()
            .putString("reservation_waiter_$reservationToken", waiterId.toString())
            .apply()
    }

    fun getReservationWaiterId(reservationToken: String): UUID? {
        val raw = prefs.getString("reservation_waiter_$reservationToken", null) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun clearReservationWaiterId(reservationToken: String) {
        prefs.edit()
            .remove("reservation_waiter_$reservationToken")
            .apply()
    }

    private fun tokenKey(type: String, localId: UUID): String = "${type}_token_$localId"

    private fun migrateLegacyPrefs() {
        if (legacyPrefs.all.isEmpty()) return

        prefs.edit {
            legacyPrefs.all.forEach { (key, value) ->
                if (value is String) {
                    putString(key, value)
                }
            }
        }
        legacyPrefs.edit().clear().apply()
    }

    companion object {
        const val LEGACY_PREFS_NAME = "remote_token_store"
        const val ENCRYPTED_PREFS_NAME = "remote_token_store_encrypted"

        fun clearForTests(context: Context) {
            SecurePreferences.clearForTests(context, LEGACY_PREFS_NAME)
            SecurePreferences.clearForTests(context, ENCRYPTED_PREFS_NAME)
        }
    }
}
