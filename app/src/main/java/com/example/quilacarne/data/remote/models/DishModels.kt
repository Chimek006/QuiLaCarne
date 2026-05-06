package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class DishSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Int,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("categoryToken") val categoryToken: String?,
    @SerializedName("ingredientTokens") val ingredientTokens: List<String>?,
    @SerializedName("isAvailable") val isAvailable: Boolean
)

data class IngredientSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("namePl") val namePl: String,
    @SerializedName("nameEn") val nameEn: String,
    @SerializedName("allergenTokens") val allergenTokens: List<String>?
)

data class CategoryDto(
    @SerializedName("token")val token: String,
    @SerializedName("name") val name: String
)

data class CategoriesData(
    @SerializedName("item") val item: List<CategoryDto>
)

data class AllergenDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)

data class AllergenDictionaryData(
    @SerializedName("item") val item: List<AllergenDto>
)