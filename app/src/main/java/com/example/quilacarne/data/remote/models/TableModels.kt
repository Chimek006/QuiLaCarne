package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class TablesData(
    @SerializedName("tables") val tables: List<TableDto>
)

data class TableDto(
    @SerializedName("token") val token: String,
    @SerializedName("tableNumber") val tableNumber: Int,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("status") val status: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class StatusDictionaryDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)