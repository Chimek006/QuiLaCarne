package com.example.quilacarne.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AllergenDictionaryData(
    @SerializedName("item") val item: List<AllergenDto>
)
