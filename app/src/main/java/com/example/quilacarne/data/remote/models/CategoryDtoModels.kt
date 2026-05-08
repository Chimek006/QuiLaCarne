package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class CategoryDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)
