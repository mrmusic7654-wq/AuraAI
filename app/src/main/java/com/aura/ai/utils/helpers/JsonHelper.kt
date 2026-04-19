package com.aura.ai.utils.helpers

import kotlinx.serialization.json.Json

object JsonHelper {
    
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    inline fun <reified T> fromJson(jsonString: String): T? {
        return try {
            json.decodeFromString<T>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
    
    inline fun <reified T> toJson(data: T): String {
        return json.encodeToString(data)
    }
}
