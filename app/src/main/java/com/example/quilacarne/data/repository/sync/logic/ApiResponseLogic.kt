package com.example.quilacarne.data.repository.sync.logic

import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.google.gson.JsonParser

internal object ApiResponseLogic {
    fun errorMessageFromBody(rawErrorBody: String?): String? {
        val raw = rawErrorBody?.takeIf { it.isNotBlank() } ?: return null

        return runCatching {
            JsonParser.parseString(raw)
                .asJsonObject
                .get("message")
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: raw
    }

    fun <T> assertWrapperSuccess(
        body: ApiResponse<T>?,
        fallbackMessage: String
    ) {
        if (body == null) return

        val hasErrorMessages = body.errorMessages?.isNotEmpty() == true
        val statusCodeLooksSuccessful = body.statusCode in 200..299
        val wrapperLooksSuccessful = body.isSuccess ||
            statusCodeLooksSuccessful ||
            (!hasErrorMessages && body.statusCode == 0)

        if (!wrapperLooksSuccessful) {
            throw Exception(body.message ?: body.errorMessages?.joinToString() ?: fallbackMessage)
        }
    }
}
