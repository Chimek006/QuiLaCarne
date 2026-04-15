package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class TablesResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("data") val data: TablesData?,
    @SerializedName("message") val message: String?,
    @SerializedName("statusCode") val statusCode: Int,
    @SerializedName("success") val success: Boolean
)

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

data class DictionaryResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("data") val data: List<StatusDictionaryDto>?,
    @SerializedName("message") val message: String?,
    @SerializedName("statusCode") val statusCode: Int,
    @SerializedName("success") val success: Boolean
)

data class StatusDictionaryDto(
    @SerializedName("name") val name: String,
    @SerializedName("displayName") val displayName: String
)