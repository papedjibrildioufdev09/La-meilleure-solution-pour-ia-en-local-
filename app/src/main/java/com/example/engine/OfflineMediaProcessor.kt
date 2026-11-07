package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Traitement multimédia 100% hors-ligne sur l'appareil.
 * Gère la mise en cache des images locales et la génération de visuels on-device.
 */
class OfflineMediaProcessor {

    /**
     * Sauvegarde une image sélectionnée par l'utilisateur dans le stockage privé de l'app
     */
    suspend fun saveImageLocally(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val attachmentsDir = File(context.filesDir, "attachments")
        if (!attachmentsDir.exists()) attachmentsDir.mkdirs()

        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val targetFile = File(attachmentsDir, fileName)

        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Impossible d'accéder à l'image")

        val outputStream = FileOutputStream(targetFile)
        val buffer = ByteArray(32 * 1024)
        var read: Int
        try {
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream.close()
        }

        targetFile
    }

    /**
     * Analyse rapide hors-ligne d'une image locale (dimensions, taille, description technique)
     */
    suspend fun analyzeImageOffline(imageFile: File): String = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            val mime = options.outMimeType ?: "image/jpeg"
            val sizeKb = imageFile.length() / 1024

            "📸 **Média attaché** : `$mime` • ${width}x${height} px • ${sizeKb} Ko\n" +
                    "Prêt pour analyse multimodale On-Device."
        } catch (_: Exception) {
            "📸 Média attaché : ${imageFile.name}"
        }
    }

    /**
     * Générateur d'art ASCII On-Device (Génération multimédia purement locale)
     */
    suspend fun convertImageToAscii(imageFile: File, maxWidth: Int = 50): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return@withContext "[Image non décodable]"
            val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val targetHeight = (maxWidth * aspectRatio * 0.55).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)

            val asciiChars = "@%#*+=-:. "
            val sb = StringBuilder()

            for (y in 0 until scaled.height) {
                for (x in 0 until scaled.width) {
                    val pixel = scaled.getPixel(x, y)
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    val index = ((1.0 - brightness) * (asciiChars.length - 1)).toInt().coerceIn(0, asciiChars.length - 1)
                    sb.append(asciiChars[index])
                }
                sb.append("\n")
            }
            scaled.recycle()
            bitmap.recycle()
            sb.toString()
        } catch (e: Exception) {
            "[Erreur rendu ASCII : ${e.localizedMessage}]"
        }
    }
}
