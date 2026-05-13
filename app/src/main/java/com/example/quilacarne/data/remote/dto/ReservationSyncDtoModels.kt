package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ReservationSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("userToken") val userToken: String?,
    @SerializedName("tableToken") val tableToken: String,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
