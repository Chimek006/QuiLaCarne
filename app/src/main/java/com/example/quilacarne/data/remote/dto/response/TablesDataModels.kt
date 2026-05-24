package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class TablesData(
    @SerializedName("tables") val tables: List<TableDto>
)
