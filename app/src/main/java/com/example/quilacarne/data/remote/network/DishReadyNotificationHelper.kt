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
import com.google.gson.JsonObject
import java.util.LinkedHashSet
import java.util.Locale

class DishReadyNotificationHelper(
    private val context: Context
) {
    private val handledKeys = LinkedHashSet<String>()

    fun notifyIfDishReady(event: RealtimeEvent) {
        val payload = event.payload
        val key = DishReadyEventLogic.notificationKey(event)
        val canNotify = payload != null &&
            DishReadyEventLogic.isReadyEvent(event) &&
            canPostNotifications() &&
            (key == null || markHandled(key))

        if (canNotify) {
            payload?.let {
                postNotification(it, key)
            }
        }
    }

    private fun postNotification(payload: JsonObject, key: String?) {
        ensureChannel()
        val dishName = DishReadyEventLogic.dishName(payload)
        val tableNumber = DishReadyEventLogic.tableNumber(payload)
        val contentText = if (!dishName.isNullOrBlank() && !tableNumber.isNullOrBlank()) {
            "$dishName - stolik $tableNumber"
        } else {
            "Nowe danie czeka na odbiór w kuchni"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Danie gotowe do odbioru")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(nextNotificationId(key), notification)
    }

    private fun markHandled(key: String): Boolean {
        return synchronized(handledKeys) {
            val added = handledKeys.add(key)
            if (handledKeys.size > MAX_HANDLED_KEYS) {
                val iterator = handledKeys.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            added
        }
    }

    private fun nextNotificationId(key: String?): Int {
        return key?.hashCode()?.let { NOTIFICATION_ID_BASE + kotlin.math.abs(it % NOTIFICATION_ID_RANGE) }
            ?: NOTIFICATION_ID_BASE
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

    private companion object {
        const val CHANNEL_ID = "dish_ready"
        const val CHANNEL_NAME = "Gotowe dania"
        const val MAX_HANDLED_KEYS = 200
        const val NOTIFICATION_ID_BASE = 8_000
        const val NOTIFICATION_ID_RANGE = 1_000
    }
}

object DishReadyEventLogic {
    private val readyStatuses = setOf(
        "READY",
        "READY_FOR_PICKUP",
        "READY_TO_SERVE",
        "PREPARED",
        "DONE",
        "GOTOWE"
    )

    fun isReadyEvent(event: RealtimeEvent): Boolean {
        return when (event.type) {
            EVENT_DISH_READY -> true
            EVENT_ORDER_ITEM_STATUS_CHANGED -> event.payload?.let(::readyStatusFromPayload) != null
            else -> false
        }
    }

    fun isReadyStatus(statusToken: String?): Boolean {
        return statusToken
            ?.trim()
            ?.uppercase(Locale.US)
            ?.let { it in readyStatuses }
            ?: false
    }

    fun notificationKey(event: RealtimeEvent): String? {
        val payload = event.payload ?: return null
        val itemToken = orderItemToken(payload)
        val statusToken = readyStatusFromPayload(payload) ?: event.type
        return itemToken?.let { "$it:$statusToken" }
            ?: fallbackKey(event, payload, statusToken)
    }

    fun dishName(payload: JsonObject): String? {
        return payload.stringAny("dishName", "productName", "itemName", "name")
            ?: payload.objectAny("dish", "product", "item")?.stringAny("name", "dishName", "productName")
    }

    fun tableNumber(payload: JsonObject): String? {
        return payload.stringAny("tableNumber", "table", "tableNo")
            ?: payload.objectAny("table", "reservation")?.stringAny("tableNumber", "tableNo", "number")
    }

    private fun readyStatusFromPayload(payload: JsonObject): String? {
        return payload.stringAny("statusToken", "status", "orderItemStatus")
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf(::isReadyStatus)
    }

    private fun orderItemToken(payload: JsonObject): String? {
        return payload.stringAny("orderItemToken", "itemToken", "token")
            ?: payload.objectAny("orderItem", "item")?.stringAny("token", "orderItemToken", "itemToken")
    }

    private fun orderToken(payload: JsonObject): String? {
        return payload.stringAny("orderToken", "order", "orderId")
            ?: payload.objectAny("order")?.stringAny("token", "orderToken", "id")
    }

    private fun fallbackKey(
        event: RealtimeEvent,
        payload: JsonObject,
        statusToken: String
    ): String? {
        val orderToken = orderToken(payload)
        val dishName = dishName(payload)
        val tableNumber = tableNumber(payload)
        return listOfNotNull(event.type, orderToken, dishName, tableNumber, statusToken)
            .takeIf { it.size > 1 }
            ?.joinToString(":")
    }

    private fun JsonObject.stringAny(vararg names: String): String? {
        return names.firstNotNullOfOrNull(::stringOrNull)
    }

    private fun JsonObject.objectAny(vararg names: String): JsonObject? {
        return names.firstNotNullOfOrNull(::objectOrNull)
    }

    private const val EVENT_DISH_READY = "DISH_READY"
    private const val EVENT_ORDER_ITEM_STATUS_CHANGED = "ORDER_ITEM_STATUS_CHANGED"
}
