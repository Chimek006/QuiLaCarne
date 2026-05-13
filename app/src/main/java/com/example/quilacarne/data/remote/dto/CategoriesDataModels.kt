package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CategoriesData(
    @SerializedName("item") val item: List<CategoryDto>
)
