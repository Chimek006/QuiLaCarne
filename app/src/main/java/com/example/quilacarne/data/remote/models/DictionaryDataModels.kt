package com.example.quilacarne.data.remote.models

import com.google.gson.annotations.SerializedName

data class DictionaryData<T>(
    @SerializedName("item") val item: List<T>? = null,
    @SerializedName("items") val items: List<T>? = null
)

fun <T> DictionaryData<T>?.values(): List<T> {
    return this?.item ?: this?.items ?: emptyList()
}
