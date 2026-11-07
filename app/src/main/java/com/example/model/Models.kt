package com.example.model

import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Modèle Hugging Face pré-configuré vérifié et disponible publiquement sans authentification
 */
data class PresetModel(
    val id: String,
    val name: String,
    val description: String,
    val repoId: String,
    val fileName: String,
    val estimatedSizeMb: Int,
    val recommendedRam: String,
    val supportsVision: Boolean = false
)

/**
 * Modèle présent localement dans le stockage interne de l'application
 */
data class LocalModel(
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val isCustomImport: Boolean,
    val supportsVision: Boolean
) {
    val sizeFormatted: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb > 1024) {
                String.format("%.2f Go", mb / 1024.0)
            } else {
                String.format("%.1f Mo", mb)
            }
        }
}

/**
 * Message de discussion
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val isStreaming: Boolean = false,
    val tokenSpeed: Float? = null,
    val isMultimediaArtifact: Boolean = false,
    val artifactType: String? = null
)

/**
 * État de chargement du moteur et du modèle (LoadingState)
 */
sealed interface LoadingState {
    data object Idle : LoadingState

    data class Downloading(
        val modelName: String,
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val speedText: String = ""
    ) : LoadingState

    data class Importing(
        val fileName: String,
        val progress: Float
    ) : LoadingState

    data class Initializing(
        val modelName: String,
        val backendName: String = "GPU"
    ) : LoadingState

    data class Ready(
        val modelName: String,
        val backendName: String,
        val modelPath: String
    ) : LoadingState

    data class Error(
        val message: String
    ) : LoadingState
}

// Alias pour rétrocompatibilité
typealias EngineState = LoadingState

/**
 * Thèmes disponibles
 */
enum class AppThemeMode(val label: String) {
    SYSTEM("Système (Auto)"),
    DARK("Sombre"),
    LIGHT("Clair")
}

/**
 * Couleurs personnalisables des bulles de messages
 */
enum class MessageColorStyle(
    val label: String,
    val primaryColor: Color,
    val onPrimaryColor: Color,
    val containerColor: Color
) {
    CYAN("Cyan Tech", Color(0xFF0284C7), Color.White, Color(0xFF004D69)),
    VIOLET("Violet Royal", Color(0xFF7C3AED), Color.White, Color(0xFF4F378B)),
    EMERALD("Émeraude", Color(0xFF059669), Color.White, Color(0xFF064E3B)),
    AMBER("Ambre Électrique", Color(0xFFD97706), Color.White, Color(0xFF78350F)),
    ROSE("Rose Néon", Color(0xFFE11D48), Color.White, Color(0xFF881337))
}

/**
 * Catalogue des modèles vérifiés 100% publics sans licence bloquante
 */
object ModelCatalog {
    val PRESETS = listOf(
        PresetModel(
            id = "qwen3.5-0.8b",
            name = "Qwen 3.5 0.8B (Recommandé)",
            description = "Modèle compact ultra-rapide. Téléchargement public direct sans authentification.",
            repoId = "litert-community/Qwen3.5-0.8B",
            fileName = "Qwen3.5-0.8B_int8.litertlm",
            estimatedSizeMb = 920,
            recommendedRam = "3 Go+",
            supportsVision = false
        ),
        PresetModel(
            id = "qwen3.5-0.8b-vl",
            name = "Qwen 3.5 0.8B Vision-Language",
            description = "Modèle multimodal avec support complet de la vision et des images en local.",
            repoId = "litert-community/Qwen3.5-0.8B",
            fileName = "Qwen3.5-0.8B-VL_int8.litertlm",
            estimatedSizeMb = 980,
            recommendedRam = "4 Go+",
            supportsVision = true
        ),
        PresetModel(
            id = "lfm3-1.7b",
            name = "LFM3 1.7B LiteRT (Mixed Int4)",
            description = "Excellente précision pour les réponses longues et le code en local.",
            repoId = "litert-community/LFM3-1.7B-LiteRT",
            fileName = "LFM3-1.7B_mixed_int4_ekv2048.litertlm",
            estimatedSizeMb = 1100,
            recommendedRam = "4 Go+",
            supportsVision = false
        ),
        PresetModel(
            id = "spark-1.7b",
            name = "Spark X2.6 1.7B Int4",
            description = "Modèle conversationnel polyvalent léger et fluide pour smartphone.",
            repoId = "litert-community/Spark-X2.6-1.7B",
            fileName = "Spark-X2.6-1.7B_int4-mlp.litertlm",
            estimatedSizeMb = 990,
            recommendedRam = "4 Go+",
            supportsVision = false
        )
    )
}

/**
 * Données de mesure d'un benchmark hors-ligne On-Device
 */
data class BenchmarkResult(
    val modelName: String,
    val backend: String,
    val ttftMs: Long,
    val decodeTokensPerSecond: Float,
    val prefillTokensPerSecond: Float,
    val totalTokensGenerated: Int,
    val totalDurationMs: Long,
    val ramUsedMb: Long,
    val rating: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("modelName", modelName)
            put("backend", backend)
            put("ttftMs", ttftMs)
            put("decodeTokensPerSecond", decodeTokensPerSecond.toDouble())
            put("prefillTokensPerSecond", prefillTokensPerSecond.toDouble())
            put("totalTokensGenerated", totalTokensGenerated)
            put("totalDurationMs", totalDurationMs)
            put("ramUsedMb", ramUsedMb)
            put("rating", rating)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): BenchmarkResult {
            return BenchmarkResult(
                modelName = json.optString("modelName", "Modèle"),
                backend = json.optString("backend", "GPU"),
                ttftMs = json.optLong("ttftMs", 0L),
                decodeTokensPerSecond = json.optDouble("decodeTokensPerSecond", 0.0).toFloat(),
                prefillTokensPerSecond = json.optDouble("prefillTokensPerSecond", 0.0).toFloat(),
                totalTokensGenerated = json.optInt("totalTokensGenerated", 0),
                totalDurationMs = json.optLong("totalDurationMs", 0L),
                ramUsedMb = json.optLong("ramUsedMb", 0L),
                rating = json.optString("rating", "Standard"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

