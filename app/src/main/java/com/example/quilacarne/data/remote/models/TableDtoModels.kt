package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class TableDto(
    @SerializedName("token") val token: String,
    @SerializedName("tableNumber") val tableNumber: Int,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("statusTokens") val statusTokens: List<String> = emptyList(),
    @SerializedName("statusToken") val statusToken: String? = null,
    @SerializedName("updatedAt") val updatedAt: String
)
