package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class TableSyncData(
    @SerializedName("items") val items: List<TableDto>,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("pageNumber") val pageNumber: Int,
    @SerializedName("totalPages") val totalPages: Int
)
