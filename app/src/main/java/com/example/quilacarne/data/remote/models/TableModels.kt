package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class TableSyncResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: TableSyncData
)

data class TableSyncData(
    @SerializedName("items") val items: List<TableDto>,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("pageNumber") val pageNumber: Int,
    @SerializedName("totalPages") val totalPages: Int
)

data class TablesData(
    @SerializedName("tables") val tables: List<TableDto>
)

data class TableDto(
    @SerializedName("token") val token: String,
    @SerializedName("tableNumber") val tableNumber: Int,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("status") val status: String,
    @SerializedName("statusToken") val statusToken: String = "",
    @SerializedName("updatedAt") val updatedAt: String
)

data class StatusDictionaryDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)