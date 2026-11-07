package com.example.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Session de discussion complète sauvegardée dans l'historique
 */
data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)

        val array = JSONArray()
        for (msg in messages) {
            val msgJson = JSONObject().apply {
                put("id", msg.id)
                put("text", msg.text)
                put("isUser", msg.isUser)
                put("timestamp", msg.timestamp)
                put("imagePath", msg.imagePath ?: "")
                put("tokenSpeed", msg.tokenSpeed?.toDouble() ?: -1.0)
                put("isMultimediaArtifact", msg.isMultimediaArtifact)
                put("artifactType", msg.artifactType ?: "")
            }
            array.put(msgJson)
        }
        json.put("messages", array)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ConversationSession {
            val id = json.optString("id", UUID.randomUUID().toString())
            val title = json.optString("title", "Nouvelle discussion")
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = json.optLong("updatedAt", System.currentTimeMillis())

            val msgsList = mutableListOf<ChatMessage>()
            val array = json.optJSONArray("messages")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val mJson = array.getJSONObject(i)
                    val speed = mJson.optDouble("tokenSpeed", -1.0)
                    val imgPath = mJson.optString("imagePath", "")
                    val artType = mJson.optString("artifactType", "")

                    msgsList.add(
                        ChatMessage(
                            id = mJson.optString("id", UUID.randomUUID().toString()),
                            text = mJson.optString("text", ""),
                            isUser = mJson.optBoolean("isUser", false),
                            timestamp = mJson.optLong("timestamp", System.currentTimeMillis()),
                            imagePath = imgPath.ifEmpty { null },
                            isStreaming = false,
                            tokenSpeed = if (speed > 0) speed.toFloat() else null,
                            isMultimediaArtifact = mJson.optBoolean("isMultimediaArtifact", false),
                            artifactType = artType.ifEmpty { null }
                        )
                    )
                }
            }

            return ConversationSession(
                id = id,
                title = title,
                createdAt = createdAt,
                updatedAt = updatedAt,
                messages = msgsList
            )
        }
    }
}

/**
 * Gestionnaire persistant de l'historique des discussions dans le stockage local
 */
class ConversationHistoryManager(context: Context) {
    private val dir = File(context.filesDir, "conversations").apply {
        if (!exists()) mkdirs()
    }

    fun getAllSessions(): List<ConversationSession> {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val list = mutableListOf<ConversationSession>()
        for (f in files) {
            try {
                val content = f.readText()
                val session = ConversationSession.fromJson(JSONObject(content))
                list.add(session)
            } catch (_: Exception) {}
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun saveSession(session: ConversationSession) {
        try {
            val file = File(dir, "${session.id}.json")
            file.writeText(session.toJson().toString())
        } catch (_: Exception) {}
    }

    fun deleteSession(sessionId: String) {
        val file = File(dir, "$sessionId.json")
        if (file.exists()) {
            file.delete()
        }
    }
}
