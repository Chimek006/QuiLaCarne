package com.example.quilacarne.data.remote.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.quilacarne.R
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.google.gson.JsonObject
import java.util.UUID

class WaiterAssignmentNotificationHelper(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val database: AppDatabase
) {
    suspend fun notifyIfAssignedToCurrentUser(event: RealtimeEvent) {
        val payload = event.payload

        if (payload != null && isCurrentUserAssigned(payload) && canPostNotifications()) {
            ensureChannel()
            val tableNumber = extractTableNumber(payload)
            val contentText = tableNumber
                ?.let { "Stolik $it" }
                ?: "Sprawdź szczegóły rezerwacji w aplikacji."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Przypisano Cię do stolika")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun isCurrentUserAssigned(payload: JsonObject): Boolean {
        val currentUsername = tokenManager.getCurrentUsername()?.trim()?.takeIf { it.isNotBlank() }
        val waiterUsername = payload.waiterUsername()

        return when {
            currentUsername == null -> false
            waiterUsername != null -> waiterUsername.equals(currentUsername, ignoreCase = true)
            else -> matchesCurrentUserToken(payload.waiterToken(), currentUsername)
        }
    }

    private suspend fun matchesCurrentUserToken(waiterToken: String?, currentUsername: String): Boolean {
        // Backend contract note: keep this aligned with WAITER_ASSIGNED payload fields once documented.
        return waiterToken?.let { token ->
            database.userDao().getUserByUsername(currentUsername)?.id == token.toStableUUID()
        } == true
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun extractTableNumber(payload: JsonObject): String? {
        return payload.stringOrNull("tableNumber")
            ?: payload.stringOrNull("table")
            ?: payload.objectOrNull("table")?.stringOrNull("tableNumber")
            ?: payload.objectOrNull("reservation")?.stringOrNull("tableNumber")
    }

    private fun JsonObject.waiterUsername(): String? {
        return stringOrNull("waiterUsername")
            ?: stringOrNull("username")
            ?: objectOrNull("waiter")?.stringOrNull("username")
    }

    private fun JsonObject.waiterToken(): String? {
        return stringOrNull("waiterToken")
            ?: stringOrNull("waiter")
            ?: objectOrNull("waiter")?.stringOrNull("token")
    }

    private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray(Charsets.UTF_8))

    private companion object {
        const val CHANNEL_ID = "waiter_assignments"
        const val CHANNEL_NAME = "Przypisania kelnera"
        const val NOTIFICATION_ID = 4_201
    }
}
