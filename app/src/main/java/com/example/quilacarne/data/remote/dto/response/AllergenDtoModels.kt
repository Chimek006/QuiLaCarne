package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class AllergenDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)
