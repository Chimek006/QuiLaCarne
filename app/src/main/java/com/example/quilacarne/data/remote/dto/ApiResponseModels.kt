package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName(value = "success", alternate = ["isSuccess"]) val isSuccess: Boolean = false,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("statusCode") val statusCode: Int = 0,
    @SerializedName("errorMessages") val errorMessages: List<String>? = null
)
