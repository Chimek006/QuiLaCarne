package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val isSuccess: Boolean,
    @SerializedName("data") val data: T?,
    @SerializedName("message") val message: String?,
    @SerializedName("statusCode") val statusCode: Int,
    @SerializedName("errorMessages") val errorMessages: List<String>?
)

data class PaginatedList<T>(
    @SerializedName("items") val items: List<T>,
    @SerializedName("pageNumber") val pageNumber: Int,
    @SerializedName("pageSize") val pageSize: Int,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("totalPages") val totalPages: Int
)

data class OrderSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("tableToken") val tableToken: String,
    @SerializedName("totalPrice") val totalPrice: Double,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class OrderItemSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("orderToken") val orderToken: String,
    @SerializedName("productToken") val productToken: String,
    @SerializedName("statusTokens") val statusTokens: List<String>,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("priceAtTimeOfOrder") val priceAtTimeOfOrder: Double,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class DictionaryItem(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)

data class BootstrapResponse(
    @SerializedName("modules") val modules: Map<String, ModuleInfo>,
    @SerializedName("serverTime") val serverTime: String
)

data class ModuleInfo(
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("totalPages") val totalPages: Int
)