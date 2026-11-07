package com.example.engine

import android.content.Context
import android.os.Debug
import com.example.model.BenchmarkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Gestionnaire d'exécution et de sauvegarde des Benchmarks hors-ligne On-Device
 */
class BenchmarkManager(private val context: Context) {

    private val benchmarkDir = File(context.filesDir, "benchmarks").apply {
        if (!exists()) mkdirs()
    }
    private val historyFile = File(benchmarkDir, "benchmark_history.json")

    /**
     * Exécute un test de performance calibré sur le moteur actif
     */
    suspend fun runBenchmark(
        llmEngine: LlmEngine,
        modelName: String,
        testPrompt: String = "Explique brièvement en trois phrases ce qu'est l'intelligence artificielle locale.",
        onProgress: (status: String) -> Unit
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        onProgress("Démarrage du benchmark hors-ligne...")

        val startMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        val sb = StringBuilder()

        onProgress("Mesure du TTFT (Time To First Token)...")

        llmEngine.chat(testPrompt, null).collect { chunk ->
            if (firstTokenTime == null) {
                firstTokenTime = System.currentTimeMillis()
                onProgress("Génération des tokens en cours...")
            }
            tokenCount++
            sb.append(chunk)
        }

        val endTime = System.currentTimeMillis()
        val ttft = if (firstTokenTime != null) firstTokenTime!! - startTime else (endTime - startTime)
        val decodeDurationSec = if (firstTokenTime != null) (endTime - firstTokenTime!!) / 1000f else (endTime - startTime) / 1000f

        val decodeTps = if (decodeDurationSec > 0.05f) {
            tokenCount / decodeDurationSec
        } else {
            tokenCount.toFloat()
        }

        val estimatedPrefillTps = if (ttft > 0) {
            (25f / (ttft / 1000f)).coerceAtLeast(10f)
        } else 30f

        val endMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        val ramUsed = (endMemory - startMemory).coerceAtLeast(35)

        val rating = when {
            decodeTps >= 25f -> "Exceptionnel ⚡⚡"
            decodeTps >= 15f -> "Très Fluide ⚡"
            decodeTps >= 8f -> "Bonne fluidité 👍"
            decodeTps >= 4f -> "Modéré ⏱️"
            else -> "Lent (CPU restreint) ⚠️"
        }

        val result = BenchmarkResult(
            modelName = modelName,
            backend = llmEngine.backendName,
            ttftMs = ttft,
            decodeTokensPerSecond = decodeTps,
            prefillTokensPerSecond = estimatedPrefillTps,
            totalTokensGenerated = tokenCount,
            totalDurationMs = endTime - startTime,
            ramUsedMb = ramUsed,
            rating = rating,
            timestamp = System.currentTimeMillis()
        )

        saveBenchmark(result)
        result
    }

    fun getBenchmarkHistory(): List<BenchmarkResult> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(historyFile.readText())
            val list = mutableListOf<BenchmarkResult>()
            for (i in 0 until jsonArray.length()) {
                list.add(BenchmarkResult.fromJson(jsonArray.getJSONObject(i)))
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveBenchmark(result: BenchmarkResult) {
        try {
            val currentList = getBenchmarkHistory().toMutableList()
            currentList.add(0, result)
            val jsonArray = JSONArray()
            for (r in currentList.take(20)) { // Conserve les 20 derniers résultats
                jsonArray.put(r.toJson())
            }
            historyFile.writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }
}
