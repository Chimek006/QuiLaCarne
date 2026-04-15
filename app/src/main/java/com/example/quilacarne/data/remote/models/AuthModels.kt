package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("data") val data: LoginData?,
    @SerializedName("message") val message: String?,
    @SerializedName("statusCode") val statusCode: Int,
    @SerializedName("errorMessages") val errorMessages: List<String>?,
    @SerializedName("success") val success: Boolean
)

data class LoginData(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("username") val username: String,
    @SerializedName("requires2fa") val requires2fa: Boolean
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)