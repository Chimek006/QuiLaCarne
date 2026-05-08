package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val isSuccess: Boolean,
    @SerializedName("data") val data: T?,
    @SerializedName("message") val message: String?,
    @SerializedName("statusCode") val statusCode: Int,
    @SerializedName("errorMessages") val errorMessages: List<String>?
)
