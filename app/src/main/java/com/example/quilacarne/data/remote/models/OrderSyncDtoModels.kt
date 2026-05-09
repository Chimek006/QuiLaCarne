package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class OrderSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("reservationToken") val reservationToken: String?,
    @SerializedName("tableToken") val tableToken: String,
    @SerializedName("waiterToken") val waiterToken: String?,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("totalPrice") val totalPrice: Int,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
