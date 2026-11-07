package com.example.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Couche d'inférence On-Device encapsulant LiteRT-LM (Google AI Edge).
 * Optimisée pour Android 8.0+ (Oreo) et versions supérieures :
 * - Détection automatique de la version d'Android pour éviter les blocages GPU sur Android 8/9
 * - Initialisation CPU directe et fluide sur Android 8.0 (API 26/27)
 * - Timeout de sécurité pour ne jamais bloquer l'interface
 * - Prévention des boucles infinies de génération
 */
class LlmEngine {

    companion object {
        private const val TAG = "LlmEngine"

        private val STOP_SEQUENCES = listOf(
            "<|im_end|>",
            "<|endoftext|>",
            "<|im_start|>",
            "<end_of_turn>",
            "<start_of_turn>",
            "</s>",
            "<eos>",
            "<|user|>",
            "<|assistant|>",
            "<|system|>"
        )

        init {
            try {
                System.loadLibrary("litertlm_jni")
            } catch (_: Throwable) {
                // Loaded by LiteRT-LM runtime
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeBackendName: String = "Inactif"
    private var currentModelPath: String? = null

    val isReady: Boolean
        get() = engine != null && conversation != null

    val backendName: String
        get() = activeBackendName

    /**
     * Initialise le moteur LiteRT-LM avec prise en charge d'Android 8.0 (API 26).
     * Sur Android 8 et 9, les pilotes GPU manquent des extensions Vulkan 1.1 / OpenCL requises
     * et provoquent des blocages natifs. Le moteur bascule donc intelligemment sur le CPU
     * qui fonctionne de manière 100% fiable avec les instructions ARM NEON.
     */
    suspend fun initialize(
        context: Context,
        modelPath: String,
        forceCpu: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        close()

        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            return@withContext Result.failure(
                IllegalArgumentException("Fichier de modèle introuvable ou trop petit (${modelFile.length()} octets)")
            )
        }

        currentModelPath = modelPath
        val cacheDir = File(context.cacheDir, "litert_cache").apply { if (!exists()) mkdirs() }
        val cacheDirPath = cacheDir.absolutePath

        // Sur Android < 10 (SDK < 29), ne jamais tenter le GPU pour éviter les blocages de pilotes
        val isAndroidQOrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val shouldTryGpu = isAndroidQOrAbove && !forceCpu

        var loadedEngine: Engine? = null
        var chosenBackend = "CPU"

        if (shouldTryGpu) {
            Log.i(TAG, "Tentative d'accélération matérielle GPU (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})...")
            try {
                // Timeout strict de 6 secondes pour éviter les blocages de compilation de shaders
                val gpuInitResult = withTimeoutOrNull(6_000L) {
                    val gpuConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        cacheDir = cacheDirPath
                    )
                    val gpuEngine = Engine(gpuConfig)
                    gpuEngine.initialize()

                    // Test de validation immédiat pour intercepter les erreurs de buffer GPU (RESOURCE_EXHAUSTED)
                    val testConv = gpuEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of("Test initialisation")
                        )
                    )
                    testConv.close()
                    gpuEngine
                }

                if (gpuInitResult != null) {
                    loadedEngine = gpuInitResult
                    chosenBackend = "GPU"
                    Log.i(TAG, "Moteur GPU LiteRT-LM initialisé et validé avec succès !")
                } else {
                    Log.w(TAG, "Délai dépassé (timeout) pour l'initialisation GPU. Bascule automatique vers le CPU.")
                }
            } catch (gpuError: Throwable) {
                Log.w(TAG, "GPU non disponible ou mémoire tampon GPU insuffisante (${gpuError.message}). Bascule vers le CPU.", gpuError)
                loadedEngine = null
            }
        } else {
            Log.i(TAG, "Initialisation directe sur CPU (Recommandé pour compatibilité Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}).")
        }

        // Si le GPU n'a pas été tenté ou a échoué, initialisation immédiate sur CPU
        if (loadedEngine == null) {
            chosenBackend = "CPU"
            try {
                // Pour le CPU, ne pas passer cacheDir pour éviter les opérations disque superflues sur Android 8
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU()
                )
                Log.i(TAG, "Démarrage de l'initialisation CPU (timeout 45s)...")
                val cpuInitResult = withTimeoutOrNull(45_000L) {
                    val cpuEngine = Engine(cpuConfig)
                    cpuEngine.initialize()
                    cpuEngine
                }

                if (cpuInitResult != null) {
                    loadedEngine = cpuInitResult
                    Log.i(TAG, "Moteur CPU LiteRT-LM initialisé avec succès et prêt.")
                } else {
                    val modelSizeMb = modelFile.length() / (1024 * 1024)
                    Log.e(TAG, "Délai d'initialisation CPU dépassé (>45s) pour le modèle de $modelSizeMb Mo.")
                    return@withContext Result.failure(
                        java.util.concurrent.TimeoutException(
                            "Le chargement du modèle ($modelSizeMb Mo) a pris trop de temps (>45s). Sur Android ${Build.VERSION.RELEASE}, la RAM ou le processeur peut être surchargé. Essayez de redémarrer ou choisissez un modèle plus léger (ex: Qwen 3.5 0.8B)."
                        )
                    )
                }
            } catch (cpuError: Throwable) {
                Log.e(TAG, "Échec fatal de l'initialisation CPU", cpuError)
                return@withContext Result.failure(
                    IllegalStateException(
                        "Impossible d'exécuter le modèle en local : mémoire RAM insuffisante ou format de modèle incompatible (${cpuError.localizedMessage})"
                    )
                )
            }
        }

        engine = loadedEngine
        activeBackendName = chosenBackend

        try {
            val convResult = withTimeoutOrNull(10_000L) {
                val convConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "Tu es un assistant IA On-Device fonctionnant 100% en local grâce à Google AI Edge LiteRT-LM. " +
                                "Réponds en français avec clarté, concision et pertinence."
                    )
                )
                loadedEngine.createConversation(convConfig)
            }
            if (convResult != null) {
                conversation = convResult
                Log.i(TAG, "Conversation prête avec backend $chosenBackend.")
            } else {
                return@withContext Result.failure(
                    java.util.concurrent.TimeoutException("Délai de création de la session conversation dépassé.")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur création session conversation", e)
            return@withContext Result.failure(e)
        }

        Result.success(chosenBackend)
    }

    /**
     * Envoie un message et émet chaque nouveau token ou segment de texte dès qu'il est disponible.
     * Prévient activement les boucles infinies via :
     * 1. Pénalité de répétition native (RepetitionPenaltyConfig)
     * 2. Interception des séquences EOS (<|im_end|>, etc.)
     * 3. Limite de sécurité maxOutputToken
     * 4. Détecteur de répétitions cycliques
     */
    fun chat(message: String, attachedImagePath: String? = null): Flow<String> = flow {
        val currentConv = conversation
        if (currentConv == null) {
            emit("Erreur : Aucun modèle LiteRT-LM n'est prêt.")
            return@flow
        }

        var accumulatedLength = 0
        val fullGeneratedText = StringBuilder()
        var tokenCount = 0
        val maxTokensLimit = 1024

        var consecutiveRepetitions = 0
        var lastSentence = ""

        val repetitionConfig = RepetitionPenaltyConfig(
            repetitionPenalty = 1.15f,
            presencePenalty = 0.1f,
            frequencyPenalty = 0.2f
        )
        val noRepeatConfig = NoRepeatNgramConfig(
            noRepeatNgramSize = 4
        )

        try {
            val incomingFlow: Flow<Message> = if (attachedImagePath != null && File(attachedImagePath).exists()) {
                val imageFile = File(attachedImagePath)
                val contents = try {
                    Contents.of(
                        Content.ImageFile(imageFile.absolutePath),
                        Content.Text(message.ifBlank { "Décris cette image en détail." })
                    )
                } catch (_: Exception) {
                    Contents.of(message)
                }
                currentConv.sendMessageAsync(
                    contents = contents,
                    repetitionPenaltyConfig = repetitionConfig,
                    noRepeatNgramConfig = noRepeatConfig,
                    maxOutputToken = maxTokensLimit
                )
            } else {
                currentConv.sendMessageAsync(
                    text = message,
                    repetitionPenaltyConfig = repetitionConfig,
                    noRepeatNgramConfig = noRepeatConfig,
                    maxOutputToken = maxTokensLimit
                )
            }

            var stopEncountered = false

            incomingFlow.collect { chunkMsg ->
                if (stopEncountered) return@collect

                val fullOrDelta = extractTextFromMessage(chunkMsg)
                if (fullOrDelta.isNotEmpty()) {
                    val delta = if (fullOrDelta.length > accumulatedLength && fullOrDelta.startsWith(fullOrDelta.take(accumulatedLength))) {
                        val diff = fullOrDelta.substring(accumulatedLength)
                        accumulatedLength = fullOrDelta.length
                        diff
                    } else {
                        accumulatedLength += fullOrDelta.length
                        fullOrDelta
                    }

                    if (delta.isNotEmpty()) {
                        tokenCount++

                        var cleanDelta = delta
                        for (stopSeq in STOP_SEQUENCES) {
                            if (cleanDelta.contains(stopSeq)) {
                                cleanDelta = cleanDelta.substringBefore(stopSeq)
                                stopEncountered = true
                                break
                            }
                        }

                        if (cleanDelta.isNotEmpty()) {
                            fullGeneratedText.append(cleanDelta)

                            if (cleanDelta.contains(".") || cleanDelta.contains("\n")) {
                                val currentSentence = cleanDelta.trim()
                                if (currentSentence.length > 10 && currentSentence == lastSentence) {
                                    consecutiveRepetitions++
                                    if (consecutiveRepetitions >= 2) {
                                        Log.w(TAG, "Boucle de répétition détectée, arrêt forcé.")
                                        stopEncountered = true
                                        return@collect
                                    }
                                } else {
                                    lastSentence = currentSentence
                                    consecutiveRepetitions = 0
                                }
                            }

                            emit(cleanDelta)
                        }

                        if (stopEncountered || tokenCount >= maxTokensLimit) {
                            stopEncountered = true
                            return@collect
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur streaming : ${e.message}", e)
            emit("\n[Fin de réponse]")
        }
    }.flowOn(Dispatchers.IO)

    private fun extractTextFromMessage(msg: Message): String {
        val contents = msg.contents
        val parts = contents.contents
        val builder = StringBuilder()
        for (part in parts) {
            if (part is Content.Text) {
                builder.append(part.text)
            }
        }
        return builder.toString()
    }

    fun resetConversation() {
        val currentEngine = engine ?: return
        try {
            conversation?.close()
        } catch (_: Exception) {}
        try {
            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(
                    "Tu es un assistant IA On-Device fonctionnant 100% en local grâce à Google AI Edge LiteRT-LM."
                )
            )
            conversation = currentEngine.createConversation(convConfig)
        } catch (_: Exception) {}
    }

    fun close() {
        try {
            conversation?.close()
        } catch (_: Exception) {}
        conversation = null

        try {
            engine?.close()
        } catch (_: Exception) {}
        engine = null
        activeBackendName = "Inactif"
    }
}
