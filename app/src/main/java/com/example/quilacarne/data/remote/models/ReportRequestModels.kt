package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class ReportCreateRequest(
    @SerializedName("clientToken") val clientToken: String,
    @SerializedName("reason") val reason: String
)

data class GuestReportSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("guestToken") val guestToken: String?,
    @SerializedName("reporterToken") val reporterToken: String?,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("reason") val reason: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
