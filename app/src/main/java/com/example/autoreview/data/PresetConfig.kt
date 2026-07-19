package com.example.autoreview.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RunHistoryEntry(
    val timestamp: Long,
    val success: Boolean,
    val message: String
)

@Serializable
data class PresetConfig(
    val defaultStarRating: Int = 4,
    val defaultBinaryChoice: String = "Yes",
    val runHistory: List<RunHistoryEntry> = emptyList()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String): PresetConfig = try {
            json.decodeFromString(serializer(), raw)
        } catch (_: Exception) {
            PresetConfig()
        }

        fun toJson(config: PresetConfig): String {
            return json.encodeToString(serializer(), config)
        }
    }
}
