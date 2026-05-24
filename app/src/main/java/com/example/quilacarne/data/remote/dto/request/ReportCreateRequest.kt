package com.example.quilacarne.data.remote.dto.request

import com.google.gson.annotations.SerializedName

data class ReportCreateRequest(
    @SerializedName("clientToken") val clientToken: String,
    @SerializedName("reason") val reason: String
)
