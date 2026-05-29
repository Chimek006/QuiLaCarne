package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class LoginData(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("username") val username: String,
    @SerializedName("requires2fa") val requires2fa: Boolean
)

data class UserProfileData(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("roles") val roles: List<String>? = emptyList(),
    @SerializedName(value = "2FaEnable", alternate = ["is2FaEnable"]) val twoFaEnabled: Boolean = false
)
