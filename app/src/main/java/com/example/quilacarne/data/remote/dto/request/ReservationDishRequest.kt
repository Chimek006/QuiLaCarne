package com.example.quilacarne.data.remote.dto.request

import com.google.gson.annotations.SerializedName

data class ReservationDishRequest(
    @SerializedName("dishToken") val dishToken: String,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("note") val note: String? = null
)