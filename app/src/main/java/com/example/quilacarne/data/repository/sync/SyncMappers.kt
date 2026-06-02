package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import com.example.quilacarne.data.remote.dto.response.OrderItemSyncDto
import com.example.quilacarne.data.remote.dto.response.OrderSyncDto
import com.example.quilacarne.data.remote.dto.response.ReservationSyncDto
import com.example.quilacarne.data.remote.dto.response.TableDto
import com.example.quilacarne.data.remote.dto.response.UserSyncDto
import com.example.quilacarne.data.repository.sync.logic.TableStatusSyncLogic
import com.example.quilacarne.utils.ReservationTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray())

internal fun getCurrentTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

internal fun TableDto.currentStatusToken(): String? {
    statusToken
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return TableStatusSyncLogic.chooseStatusToken(statusTokens)
}

internal fun String.toTableStatusName(): String {
    return TableStatusSyncLogic.tableStatusNamePl(this)
}

internal fun String.toTableStatusNameEn(): String {
    return TableStatusSyncLogic.tableStatusNameEn(this)
}

internal fun ReservationSyncDto.toReservationEntity(): ReservationEntity {
    return ReservationEntity(
        id = token.toStableUUID(),
        token = token,
        tableId = tableToken.toStableUUID(),
        tableToken = tableToken,
        userToken = userToken,
        startTime = startTime,
        endTime = endTime,
        startEpochMillis = ReservationTimeUtils.parseApiTimestampMillis(startTime) ?: 0L,
        endEpochMillis = ReservationTimeUtils.parseApiTimestampMillis(endTime) ?: 0L,
        statusTokens = ReservationTimeUtils.statusTokensToText(statusTokens),
        isActive = ReservationTimeUtils.isActiveStatus(statusTokens),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

internal fun OrderSyncDto.toOrderEntity(): OrderEntity {
    return OrderEntity(
        id = token.toStableUUID(),
        tableId = tableToken.toStableUUID(),
        waiterId = waiterToken?.toStableUUID(),
        statusId = null,
        statusTokens = statusTokens.joinToString(","),
        totalPrice = totalPrice,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

internal fun OrderItemSyncDto.toOrderItemEntity(): OrderItemEntity {
    return OrderItemEntity(
        id = token.toStableUUID(),
        orderId = orderToken.toStableUUID(),
        productId = productToken.toStableUUID(),
        quantity = quantity,
        priceAtTimeOfOrder = priceAtTimeOfOrder,
        statusTokens = statusTokens.joinToString(","),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

internal fun UserSyncDto.toUsersEntity(preservedPasswords: Map<String, String>): UsersEntity {
    val role = roleTokens.orEmpty().joinToString(",").ifBlank { if (isStaff) "ROLE_WAITER" else "ROLE_CLIENT" }
    val preservedPassword = preservedPasswords[username.normalizedLoginKey()]
        ?: email?.normalizedLoginKey()?.let { preservedPasswords[it] }

    return UsersEntity(
        id = token.toStableUUID(),
        username = username,
        password = preservedPassword.orEmpty(),
        isActive = isActive ?: true,
        role = role,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

internal fun List<UsersEntity>.toPreservedPasswordMap(): Map<String, String> {
    return flatMap { user ->
        listOf(user.username.normalizedLoginKey() to user.password)
    }.toMap()
}

internal fun List<UsersEntity>.findByUsername(username: String): UsersEntity? {
    val normalizedUsername = username.normalizedLoginKey()
    return firstOrNull { user -> user.username.normalizedLoginKey() == normalizedUsername }
}

internal fun String.normalizedLoginKey(): String {
    return trim().lowercase(Locale.US)
}
