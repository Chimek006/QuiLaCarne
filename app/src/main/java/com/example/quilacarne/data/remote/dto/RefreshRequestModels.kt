package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)
