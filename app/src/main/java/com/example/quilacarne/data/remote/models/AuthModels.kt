package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
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

data class TokenResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String
)