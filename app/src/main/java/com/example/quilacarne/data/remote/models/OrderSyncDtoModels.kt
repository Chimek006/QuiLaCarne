package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class OrderSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("tableToken") val tableToken: String,
    @SerializedName("totalPrice") val totalPrice: Double,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
