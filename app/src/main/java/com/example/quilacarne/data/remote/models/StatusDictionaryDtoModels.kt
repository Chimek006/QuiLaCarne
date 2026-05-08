package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class StatusDictionaryDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)
