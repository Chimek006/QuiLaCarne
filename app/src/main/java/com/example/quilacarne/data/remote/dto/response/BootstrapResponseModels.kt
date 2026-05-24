package com.example.quilacarne.data.remote.dto.response

import com.google.gson.annotations.SerializedName

data class BootstrapResponse(
    @SerializedName("modules") val modules: Map<String, ModuleInfo>,
    @SerializedName("serverTime") val serverTime: String
)
