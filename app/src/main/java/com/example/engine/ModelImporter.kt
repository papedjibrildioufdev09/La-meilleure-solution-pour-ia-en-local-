package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Gestionnaire d'importation et d'exportation de fichiers de modèles (.litertlm, .bin, .tflite).
 * Compatible avec Android 8.0+ (Oreo, API 26) et versions supérieures.
 */
class ModelImporter(private val downloader: ModelDownloader = ModelDownloader()) {

    companion object {
        private const val TAG = "ModelImporter"
    }

    /**
     * Extrait le nom réel du fichier depuis un Content Uri
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "custom_model.litertlm"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val displayName = cursor.getString(nameIndex)
                        if (!displayName.isNullOrBlank()) {
                            name = displayName
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de lire le nom du fichier via contentResolver", e)
            }
        } else if (uri.path != null) {
            val file = File(uri.path!!)
            name = file.name
        }

        if (!name.endsWith(".litertlm") && !name.endsWith(".bin") && !name.endsWith(".tflite")) {
            name = "$name.litertlm"
        }
        return name
    }

    /**
     * Copie le fichier sélectionné (Uri) vers le stockage interne privé de l'app
     */
    suspend fun importModelFromUri(
        context: Context,
        uri: Uri,
        onProgress: (progress: Float, copiedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val fileName = getFileNameFromUri(context, uri)
        val targetDir = downloader.getModelsDirectory(context)
        val targetFile = File(targetDir, fileName)

        val contentResolver = context.contentResolver
        val totalBytes = try {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) {
            -1L
        }

        val inputStream: InputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Impossible d'ouvrir le flux de données pour $uri")

        val tempFile = File(targetDir, "$fileName.importing")
        val outputStream = FileOutputStream(tempFile)

        val buffer = ByteArray(64 * 1024)
        var copiedBytes = 0L
        var lastUpdateTime = System.currentTimeMillis()

        try {
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                copiedBytes += read

                val now = System.currentTimeMillis()
                if (now - lastUpdateTime > 200 || copiedBytes == totalBytes) {
                    val progress = if (totalBytes > 0) (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
                    onProgress(progress, copiedBytes, totalBytes)
                    lastUpdateTime = now
                }
            }
            outputStream.flush()
        } finally {
            try {
                inputStream.close()
                outputStream.close()
            } catch (_: Exception) {}
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        onProgress(1.0f, targetFile.length(), targetFile.length())
        targetFile
    }

    /**
     * Importe un modèle directement depuis un chemin de fichier absolu du stockage
     * (ex: /sdcard/Download/model.litertlm)
     */
    suspend fun importFromLocalPath(
        context: Context,
        filePath: String,
        onProgress: (progress: Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val sourceFile = File(filePath)
        if (!sourceFile.exists() || sourceFile.length() < 1024 * 1024) {
            throw IllegalArgumentException("Fichier introuvable ou vide : $filePath")
        }

        val targetDir = downloader.getModelsDirectory(context)
        val targetFile = File(targetDir, sourceFile.name)

        val inputStream = FileInputStream(sourceFile)
        val tempFile = File(targetDir, "${sourceFile.name}.importing")
        val outputStream = FileOutputStream(tempFile)

        val buffer = ByteArray(64 * 1024)
        val totalBytes = sourceFile.length()
        var copiedBytes = 0L

        try {
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                copiedBytes += read
                val progress = if (totalBytes > 0) (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
                onProgress(progress)
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream.close()
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        onProgress(1.0f)
        targetFile
    }

    /**
     * Exporte un modèle vers le dossier Téléchargements public du téléphone
     * avec fallback sécurisé pour Android 8.0+ (Oreo).
     */
    suspend fun exportModelToDownloads(
        context: Context,
        modelFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val destination = if (downloadsDir.canWrite()) {
                File(downloadsDir, modelFile.name)
            } else {
                val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                File(appDownloads, modelFile.name)
            }

            modelFile.copyTo(destination, overwrite = true)
            Log.i(TAG, "Modèle exporté avec succès vers ${destination.absolutePath}")
            Result.success(destination)
        } catch (e: Exception) {
            // Tentative de fallback sur le stockage externe privé de l'app (accessible sans permission)
            try {
                val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val fallbackDest = File(fallbackDir, modelFile.name)
                modelFile.copyTo(fallbackDest, overwrite = true)
                Log.i(TAG, "Modèle exporté via fallback vers ${fallbackDest.absolutePath}")
                Result.success(fallbackDest)
            } catch (e2: Exception) {
                Log.e(TAG, "Échec de l'exportation du modèle", e2)
                Result.failure(e2)
            }
        }
    }

    /**
     * Exporte un modèle vers une Uri SAF (choisie par l'utilisateur via le sélecteur Android)
     */
    suspend fun exportModelToUri(
        context: Context,
        modelFile: File,
        targetUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(IllegalStateException("Impossible d'écrire sur la destination sélectionnée"))
            val inputStream = FileInputStream(modelFile)

            outputStream.use { out ->
                inputStream.use { inp ->
                    inp.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            Log.i(TAG, "Modèle exporté avec succès vers Uri $targetUri")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'exportation vers Uri", e)
            Result.failure(e)
        }
    }
}

