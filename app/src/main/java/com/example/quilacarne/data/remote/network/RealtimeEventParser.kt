package com.example.quilacarne.data.remote.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

data class RealtimeEvent(
    val type: String,
    val payload: JsonObject?
)

object RealtimeEventParser {
    fun parse(text: String): RealtimeEvent? {
        return runCatching {
            JsonParser.parseString(text).asJsonObject
        }.getOrNull()?.let(::parseObject)
    }

    private fun parseObject(root: JsonObject): RealtimeEvent? {
        val rawType = root.stringOrNull("type")
            ?: root.stringOrNull("event")
            ?: root.stringOrNull("name")
        return rawType?.let {
            RealtimeEvent(
                type = it.trim().uppercase(Locale.US),
                payload = root.objectOrNull("payload") ?: root
            )
        }
    }
}

internal fun JsonObject.stringOrNull(name: String): String? {
    val primitive = get(name)
        ?.takeIf { it.isJsonPrimitive }
        ?.asJsonPrimitive
    val value = primitive
        ?.takeIf { it.isString || it.isNumber }
        ?.asString
    return value?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.objectOrNull(name: String): JsonObject? {
    val element = get(name) ?: return null
    return element.takeIf { it.isJsonObject }?.asJsonObject
}
