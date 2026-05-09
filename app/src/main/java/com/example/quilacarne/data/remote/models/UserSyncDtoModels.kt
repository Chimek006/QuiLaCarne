package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class UserSyncDto(
    @SerializedName("token") val token: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String?,
    @SerializedName("isActive") val isActive: Boolean?,
    @SerializedName("isStaff") val isStaff: Boolean,
    @SerializedName("roleTokens") val roleTokens: List<String>?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)
