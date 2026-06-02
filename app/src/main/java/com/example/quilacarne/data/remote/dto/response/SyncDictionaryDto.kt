package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class SyncDictionaryDto(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String?,
    @SerializedName("namePl") val namePl: String?,
    @SerializedName("nameEn") val nameEn: String?
)
{
    fun polishName(): String = namePl ?: name ?: nameEn ?: token
    fun englishName(): String = nameEn ?: name ?: namePl ?: token
}