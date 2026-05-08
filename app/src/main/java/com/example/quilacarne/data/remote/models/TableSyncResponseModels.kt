package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class TableSyncResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: TableSyncData
)
