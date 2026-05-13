package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DictionaryItem(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String
)
