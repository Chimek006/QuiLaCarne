package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginData(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("username") val username: String,
    @SerializedName("requires2fa") val requires2fa: Boolean
)
