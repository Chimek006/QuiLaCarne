package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PaginatedList<T>(
    @SerializedName("items") val items: List<T>,
    @SerializedName("pageNumber") val pageNumber: Int,
    @SerializedName("pageSize") val pageSize: Int,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("totalPages") val totalPages: Int
)
