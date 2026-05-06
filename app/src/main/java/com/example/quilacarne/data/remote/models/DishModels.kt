package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class DishSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Int,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("categoryName") val categoryName: String,
    @SerializedName("active") val isActive: Boolean,
    @SerializedName("ingredients") val ingredients: List<IngredientDto>
)

data class IngredientDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String,
    @SerializedName("allergens") val allergens: List<String>
)

data class CategoryDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)

data class CategoriesData(
    @SerializedName("categories") val categories: List<CategoryDto>
)

data class IngredientSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("namePl") val namePl: String,
    @SerializedName("nameEn") val nameEn: String,
    @SerializedName("allergenTokens") val allergenTokens: List<String>
)

data class AllergenDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)

data class AllergenDictionaryData(
    @SerializedName("allergens") val allergens: List<AllergenDto>
)