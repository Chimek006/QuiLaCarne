package com.example.quilacarne.data.remote.dto.request

import com.google.gson.annotations.SerializedName

data class ReservationCreateRequest(
    @SerializedName("dishes") val dishes: List<ReservationDishRequest> = emptyList(),
    @SerializedName("tableToken") val tableToken: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String
)
