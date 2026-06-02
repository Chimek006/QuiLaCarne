package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class DishSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Int,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("categoryToken") val categoryToken: String?,
    @SerializedName("ingredientTokens") val ingredientTokens: List<String>?,
    @SerializedName(value = "isAvailable", alternate = ["available"]) val isAvailable: Boolean? = null,
    @SerializedName(value = "isDeleted", alternate = ["deleted"]) val isDeleted: Boolean? = null,
    @SerializedName("deletedAt") val deletedAt: String? = null
)
