package com.example.quilacarne.data.remote.dto.request

import com.google.gson.annotations.SerializedName

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)
