package com.example.quilacarne.data.remote.dto.response

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class WebSocketEvent(
    @SerializedName("eventType") val eventType: String?,
    @SerializedName("entityType") val entityType: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("payload") val payload: JsonElement?,
    @SerializedName("timestamp") val timestamp: String?
)

