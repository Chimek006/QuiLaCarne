package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class TableSyncResponse(
    @SerializedName(value = "isSuccess", alternate = ["success"]) val isSuccess: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: TableSyncData
)
