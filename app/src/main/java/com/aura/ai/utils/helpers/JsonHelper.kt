package com.aura.ai.utils.helpers

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object JsonHelper {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    inline fun <reified T> fromJson(jsonString: String): T {
        return json.decodeFromString(jsonString)
    }

    inline fun <reified T> toJson(data: T): String {
        return json.encodeToString(data)
    }
}
