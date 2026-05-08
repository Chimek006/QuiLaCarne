package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class OrderItemSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("orderToken") val orderToken: String,
    @SerializedName("productToken") val productToken: String,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("priceAtTimeOfOrder") val priceAtTimeOfOrder: Double,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
