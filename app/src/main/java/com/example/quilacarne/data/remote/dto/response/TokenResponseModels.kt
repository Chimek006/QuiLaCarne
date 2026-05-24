package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String
)
