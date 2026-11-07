package com.example.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Exception personnalisée levée en cas d'échec du téléchargement du modèle
 */
class DownloadFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Gestionnaire de téléchargement hors-ligne de modèles Hugging Face
 * Utilise OkHttp avec suivi des redirections, reprise (Range) et calcul de débit.
 */
class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    fun getModelsDirectory(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isModelDownloaded(context: Context, fileName: String): Boolean {
        val file = File(getModelsDirectory(context), fileName)
        return file.exists() && file.length() > 1024 * 1024
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        val file = File(getModelsDirectory(context), fileName)
        val temp = File(getModelsDirectory(context), "$fileName.downloading")
        if (temp.exists()) temp.delete()
        return if (file.exists()) file.delete() else true
    }

    /**
     * Télécharge le modèle depuis Hugging Face avec gestion des redirections CDN,
     * reprise automatique et gestion robuste des erreurs réseau et HTTP.
     */
    suspend fun downloadModelIfNeeded(
        context: Context,
        repoId: String,
        fileName: String,
        hfToken: String? = null,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long, speedFormatted: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val targetDir = getModelsDirectory(context)
        val targetFile = File(targetDir, fileName)
        val tempFile = File(targetDir, "$fileName.downloading")

        // 1. Si le fichier complet existe déjà localement, pas d'appel réseau
        if (targetFile.exists() && targetFile.length() > 1024 * 1024) {
            onProgress(1.0f, targetFile.length(), targetFile.length(), "Déjà présent localement")
            return@withContext targetFile
        }

        try {
            val existingLength = if (tempFile.exists()) tempFile.length() else 0L
            val url = "https://huggingface.co/$repoId/resolve/main/$fileName"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "LiteRT-LM-Android/1.0 (Mobile; Android)")

            // Ajout du token Hugging Face si fourni par l'utilisateur
            if (!hfToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${hfToken.trim()}")
            }

            if (existingLength > 0) {
                requestBuilder.header("Range", "bytes=$existingLength-")
            }

            val response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: IOException) {
                throw DownloadFailedException("Erreur de connexion : vérifiez votre connexion Internet (${e.localizedMessage})", e)
            }

            if (!response.isSuccessful && response.code != 206) {
                val code = response.code
                response.close()
                val errorDetails = when (code) {
                    401, 403 -> "Accès restreint au modèle ($repoId). Le dépôt nécessite un jeton d'accès Hugging Face (HF_TOKEN) ou une acceptation de licence. Choisissez un modèle recommandé (Qwen3.5, LFM3) ou fournissez un jeton."
                    404 -> "Fichier introuvable sur Hugging Face : $repoId / $fileName"
                    500, 502, 503, 504 -> "Serveur Hugging Face temporairement inaccessible (Erreur $code)."
                    else -> "Échec du téléchargement (Code HTTP $code : ${response.message})"
                }
                throw DownloadFailedException(errorDetails)
            }

            val responseBody = response.body ?: run {
                response.close()
                throw DownloadFailedException("Réponse vide reçue de Hugging Face.")
            }

            val isResumed = response.code == 206
            val contentLength = responseBody.contentLength()
            val totalBytes = if (isResumed) existingLength + contentLength else contentLength

            val raf = try {
                RandomAccessFile(tempFile, "rw").apply {
                    if (isResumed) {
                        seek(existingLength)
                    } else {
                        setLength(0)
                        seek(0)
                    }
                }
            } catch (e: IOException) {
                response.close()
                throw DownloadFailedException("Erreur d'écriture sur le stockage local : ${e.localizedMessage}", e)
            }

            var downloadedBytes = if (isResumed) existingLength else 0L
            val buffer = ByteArray(64 * 1024)
            val inputStream = responseBody.byteStream()

            var lastProgressUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var currentSpeedText = "0 Mo/s"

            try {
                while (coroutineContext.isActive) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    downloadedBytes += read
                    bytesSinceLastUpdate += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastProgressUpdateTime
                    if (elapsed >= 400) {
                        val bytesPerSec = (bytesSinceLastUpdate * 1000.0) / elapsed
                        val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
                        currentSpeedText = String.format("%.2f Mo/s", mbPerSec)
                        lastProgressUpdateTime = now
                        bytesSinceLastUpdate = 0L

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        onProgress(progress, downloadedBytes, totalBytes, currentSpeedText)
                    }
                }
            } catch (e: Exception) {
                if (!coroutineContext.isActive) {
                    throw kotlinx.coroutines.CancellationException("Téléchargement annulé par l'utilisateur")
                }
                throw DownloadFailedException("Interruption du transfert : ${e.localizedMessage}", e)
            } finally {
                try {
                    inputStream.close()
                    raf.close()
                    response.close()
                } catch (_: Exception) {}
            }

            if (!coroutineContext.isActive) {
                throw kotlinx.coroutines.CancellationException("Téléchargement annulé")
            }

            if (tempFile.length() < 1024 * 1024) {
                throw DownloadFailedException("Le fichier téléchargé est incomplet ou trop petit.")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(1.0f, targetFile.length(), targetFile.length(), "Terminé")
            targetFile
        } catch (e: DownloadFailedException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DownloadFailedException("Erreur imprévue : ${e.localizedMessage}", e)
        }
    }
}
