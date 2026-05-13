package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ModuleInfo(
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("totalPages") val totalPages: Int
)
