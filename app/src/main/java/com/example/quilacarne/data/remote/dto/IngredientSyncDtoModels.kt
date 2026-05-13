package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class IngredientSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("namePl") val namePl: String,
    @SerializedName("nameEn") val nameEn: String,
    @SerializedName("allergenTokens") val allergenTokens: List<String>?
)
