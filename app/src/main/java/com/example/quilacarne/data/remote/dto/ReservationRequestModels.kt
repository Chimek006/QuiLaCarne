package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ReservationCreateRequest(
    @SerializedName("dishes") val dishes: List<ReservationDishRequest> = emptyList(),
    @SerializedName("tableToken") val tableToken: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String
)

data class ReservationDishRequest(
    @SerializedName("dishToken") val dishToken: String,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("note") val note: String? = null
)
