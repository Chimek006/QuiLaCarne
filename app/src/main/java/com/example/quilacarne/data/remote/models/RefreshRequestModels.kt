package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)
