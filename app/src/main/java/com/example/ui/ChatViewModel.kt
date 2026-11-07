package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.BenchmarkManager
import com.example.engine.DownloadFailedException
import com.example.engine.LlmEngine
import com.example.engine.ModelDownloader
import com.example.engine.ModelImporter
import com.example.engine.OfflineMediaProcessor
import com.example.model.AppThemeMode
import com.example.model.BenchmarkResult
import com.example.model.ChatMessage
import com.example.model.ConversationHistoryManager
import com.example.model.ConversationSession
import com.example.model.LoadingState
import com.example.model.LocalModel
import com.example.model.MessageColorStyle
import com.example.model.ModelCatalog
import com.example.model.PresetModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * ViewModel principal :
 * - Gestion du moteur On-Device LiteRT-LM
 * - Historique persistant des discussions (Sessions multiples)
 * - Module de Benchmark hors-ligne
 * - Streaming fluide anti-blocage
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val PREFS_NAME = "litert_ai_settings"
        private const val KEY_FIRST_LAUNCH = "first_launch_done"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_COLOR_STYLE = "message_color_style"
        private const val KEY_SAVED_MODEL_FILE = "saved_model_file"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val KEY_FORCE_CPU = "force_cpu_mode"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloader = ModelDownloader()
    private val importer = ModelImporter(downloader)
    private val mediaProcessor = OfflineMediaProcessor()
    private val historyManager = ConversationHistoryManager(application)
    private val benchmarkManager = BenchmarkManager(application)
    val llmEngine = LlmEngine()

    // État persistant du chargement du modèle
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    // Session de discussion courante
    private val _currentSessionId = MutableStateFlow(UUID.randomUUID().toString())
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    // Liste des messages (démarre vide sans message de bienvenue)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Historique des sessions de discussion
    private val _savedSessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    val savedSessions: StateFlow<List<ConversationSession>> = _savedSessions.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _localModels = MutableStateFlow<List<LocalModel>>(emptyList())
    val localModels: StateFlow<List<LocalModel>> = _localModels.asStateFlow()

    private val _attachedImagePath = MutableStateFlow<String?>(null)
    val attachedImagePath: StateFlow<String?> = _attachedImagePath.asStateFlow()

    private val _selectedModelName = MutableStateFlow<String?>(null)
    val selectedModelName: StateFlow<String?> = _selectedModelName.asStateFlow()

    // Benchmark hors-ligne
    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkStatus = MutableStateFlow("")
    val benchmarkStatus: StateFlow<String> = _benchmarkStatus.asStateFlow()

    private val _latestBenchmarkResult = MutableStateFlow<BenchmarkResult?>(null)
    val latestBenchmarkResult: StateFlow<BenchmarkResult?> = _latestBenchmarkResult.asStateFlow()

    private val _benchmarkHistory = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkHistory: StateFlow<List<BenchmarkResult>> = _benchmarkHistory.asStateFlow()

    // Préférences utilisateur
    private val _themeMode = MutableStateFlow(
        AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _messageColorStyle = MutableStateFlow(
        MessageColorStyle.valueOf(prefs.getString(KEY_COLOR_STYLE, MessageColorStyle.CYAN.name) ?: MessageColorStyle.CYAN.name)
    )
    val messageColorStyle: StateFlow<MessageColorStyle> = _messageColorStyle.asStateFlow()

    private val _hfToken = MutableStateFlow(prefs.getString(KEY_HF_TOKEN, "") ?: "")
    val hfToken: StateFlow<String> = _hfToken.asStateFlow()

    private val _forceCpu = MutableStateFlow(
        prefs.getBoolean(KEY_FORCE_CPU, true)
    )
    val forceCpu: StateFlow<Boolean> = _forceCpu.asStateFlow()

    private val _showSetupDialog = MutableStateFlow(!prefs.getBoolean(KEY_FIRST_LAUNCH, false))
    val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()

    private var currentInferenceJob: Job? = null
    private var currentDownloadJob: Job? = null
    private var currentInitJob: Job? = null

    init {
        refreshLocalModels()
        refreshHistorySessions()
        refreshBenchmarkHistory()
        initModelOnStartup()
    }

    private fun initModelOnStartup() {
        viewModelScope.launch {
            val dir = downloader.getModelsDirectory(getApplication())
            val existing = dir.listFiles { f ->
                f.isFile && (f.name.endsWith(".litertlm") || f.name.endsWith(".bin")) && f.length() > 1024 * 1024
            }

            if (!existing.isNullOrEmpty()) {
                val modelFile = existing.first()
                initializeEngineWithModel(modelFile.nameWithoutExtension, modelFile.absolutePath)
            } else if (prefs.getBoolean(KEY_FIRST_LAUNCH, false)) {
                val defaultPreset = ModelCatalog.PRESETS.first()
                downloadPresetModel(defaultPreset)
            } else {
                _loadingState.value = LoadingState.Idle
            }
        }
    }

    fun refreshLocalModels() {
        viewModelScope.launch {
            val dir = downloader.getModelsDirectory(getApplication())
            val files = dir.listFiles { file ->
                file.isFile && (file.name.endsWith(".litertlm") || file.name.endsWith(".bin")) && file.length() > 1024 * 1024
            } ?: emptyArray()

            val models = files.map { file ->
                val isPreset = ModelCatalog.PRESETS.any { it.fileName == file.name }
                LocalModel(
                    name = file.nameWithoutExtension,
                    file = file,
                    sizeBytes = file.length(),
                    isCustomImport = !isPreset,
                    supportsVision = file.name.contains("vl", ignoreCase = true) || file.name.contains("vision", ignoreCase = true)
                )
            }
            _localModels.value = models
        }
    }

    fun refreshHistorySessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = historyManager.getAllSessions()
            _savedSessions.value = list
        }
    }

    fun refreshBenchmarkHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _benchmarkHistory.value = benchmarkManager.getBenchmarkHistory()
        }
    }

    // Gestion de l'historique
    fun createNewSession() {
        saveCurrentSession()
        llmEngine.resetConversation()
        _currentSessionId.value = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _attachedImagePath.value = null
    }

    fun loadSession(session: ConversationSession) {
        saveCurrentSession()
        llmEngine.resetConversation()
        _currentSessionId.value = session.id
        _messages.value = session.messages
        _attachedImagePath.value = null
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyManager.deleteSession(sessionId)
            refreshHistorySessions()
            if (_currentSessionId.value == sessionId) {
                withContext(Dispatchers.Main) {
                    createNewSession()
                }
            }
        }
    }

    private fun saveCurrentSession() {
        val currentMsgs = _messages.value
        if (currentMsgs.isEmpty()) return

        val firstUserMsg = currentMsgs.firstOrNull { it.isUser }?.text?.take(40) ?: "Discussion"
        val session = ConversationSession(
            id = _currentSessionId.value,
            title = firstUserMsg,
            updatedAt = System.currentTimeMillis(),
            messages = currentMsgs
        )
        viewModelScope.launch(Dispatchers.IO) {
            historyManager.saveSession(session)
            refreshHistorySessions()
        }
    }

    // Gestion du Benchmark
    fun startOfflineBenchmark() {
        if (_isBenchmarking.value) return
        val currentModel = _selectedModelName.value ?: "Modèle LiteRT-LM"

        viewModelScope.launch {
            _isBenchmarking.value = true
            _benchmarkStatus.value = "Préparation du test..."
            try {
                val result = benchmarkManager.runBenchmark(
                    llmEngine = llmEngine,
                    modelName = currentModel,
                    onProgress = { status ->
                        _benchmarkStatus.value = status
                    }
                )
                _latestBenchmarkResult.value = result
                refreshBenchmarkHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur benchmark", e)
                _benchmarkStatus.value = "Erreur pendant le test : ${e.localizedMessage}"
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun completeInitialSetup(
        chosenPreset: PresetModel?,
        theme: AppThemeMode,
        colorStyle: MessageColorStyle,
        token: String,
        forceCpu: Boolean = _forceCpu.value
    ) {
        prefs.edit()
            .putBoolean(KEY_FIRST_LAUNCH, true)
            .putString(KEY_THEME_MODE, theme.name)
            .putString(KEY_COLOR_STYLE, colorStyle.name)
            .putString(KEY_HF_TOKEN, token)
            .putBoolean(KEY_FORCE_CPU, forceCpu)
            .apply()

        _themeMode.value = theme
        _messageColorStyle.value = colorStyle
        _hfToken.value = token
        _forceCpu.value = forceCpu
        _showSetupDialog.value = false

        if (chosenPreset != null) {
            downloadPresetModel(chosenPreset)
        }
    }

    fun setForceCpu(force: Boolean) {
        _forceCpu.value = force
        prefs.edit().putBoolean(KEY_FORCE_CPU, force).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setMessageColorStyle(style: MessageColorStyle) {
        _messageColorStyle.value = style
        prefs.edit().putString(KEY_COLOR_STYLE, style.name).apply()
    }

    fun setHfToken(token: String) {
        _hfToken.value = token
        prefs.edit().putString(KEY_HF_TOKEN, token).apply()
    }

    fun openSetupDialog() {
        _showSetupDialog.value = true
    }

    fun closeSetupDialog() {
        _showSetupDialog.value = false
    }

    fun downloadPresetModel(preset: PresetModel) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            _loadingState.value = LoadingState.Downloading(
                modelName = preset.name,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = preset.estimatedSizeMb.toLong() * 1024 * 1024,
                speedText = "Connexion..."
            )

            try {
                val token = _hfToken.value.ifBlank { null }
                val downloadedFile = downloader.downloadModelIfNeeded(
                    context = getApplication(),
                    repoId = preset.repoId,
                    fileName = preset.fileName,
                    hfToken = token,
                    onProgress = { progress, downloaded, total, speed ->
                        _loadingState.value = LoadingState.Downloading(
                            modelName = preset.name,
                            progress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            speedText = speed
                        )
                    }
                )

                refreshLocalModels()
                initializeEngineWithModel(preset.name, downloadedFile.absolutePath)

            } catch (e: DownloadFailedException) {
                Log.e(TAG, "Échec du téléchargement : ${e.message}", e)
                _loadingState.value = LoadingState.Error(
                    message = "Échec du téléchargement : ${e.message}"
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _loadingState.value = LoadingState.Idle
            } catch (e: Exception) {
                _loadingState.value = LoadingState.Error(
                    message = "Erreur inattendue : ${e.localizedMessage ?: "Erreur réseau"}"
                )
            }
        }
    }

    fun downloadCustomHuggingFaceModel(repoId: String, fileName: String) {
        val cleanRepo = repoId.trim()
        val cleanFile = fileName.trim()
        if (cleanRepo.isBlank() || cleanFile.isBlank()) {
            _loadingState.value = LoadingState.Error("Dépôt ou nom de fichier invalide.")
            return
        }

        val customPreset = PresetModel(
            id = "custom_$cleanFile",
            name = cleanFile.removeSuffix(".litertlm"),
            description = "Modèle Hugging Face ($cleanRepo)",
            repoId = cleanRepo,
            fileName = if (cleanFile.endsWith(".litertlm")) cleanFile else "$cleanFile.litertlm",
            estimatedSizeMb = 1000,
            recommendedRam = "4 Go+"
        )
        downloadPresetModel(customPreset)
    }

    fun cancelDownload() {
        currentDownloadJob?.cancel()
        _loadingState.value = LoadingState.Idle
    }

    fun importCustomModel(uri: Uri) {
        viewModelScope.launch {
            val fileName = importer.getFileNameFromUri(getApplication(), uri)
            _loadingState.value = LoadingState.Importing(fileName = fileName, progress = 0f)

            try {
                val copiedFile = importer.importModelFromUri(
                    context = getApplication(),
                    uri = uri,
                    onProgress = { progress, _, _ ->
                        _loadingState.value = LoadingState.Importing(fileName = fileName, progress = progress)
                    }
                )

                refreshLocalModels()
                initializeEngineWithModel(copiedFile.nameWithoutExtension, copiedFile.absolutePath)

            } catch (e: Exception) {
                Log.e(TAG, "Erreur importation modèle", e)
                _loadingState.value = LoadingState.Error("Échec de l'importation : ${e.localizedMessage}")
            }
        }
    }

    fun initializeEngineWithModel(modelName: String, modelPath: String) {
        currentInitJob?.cancel()
        currentInitJob = viewModelScope.launch {
            val forceCpuPref = _forceCpu.value || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
            _selectedModelName.value = modelName
            _loadingState.value = LoadingState.Initializing(
                modelName = modelName,
                backendName = if (forceCpuPref) "CPU (Recommandé)" else "GPU"
            )

            val result = llmEngine.initialize(getApplication(), modelPath, forceCpu = forceCpuPref)
            result.onSuccess { backend ->
                _loadingState.value = LoadingState.Ready(
                    modelName = modelName,
                    backendName = backend,
                    modelPath = modelPath
                )
                prefs.edit().putString(KEY_SAVED_MODEL_FILE, modelPath).apply()
            }.onFailure { error ->
                Log.e(TAG, "Échec d'initialisation LiteRT-LM", error)
                _loadingState.value = LoadingState.Error(
                    message = "Échec d'initialisation : ${error.localizedMessage}"
                )
            }
        }
    }

    fun cancelInitialization() {
        currentInitJob?.cancel()
        _loadingState.value = LoadingState.Idle
    }

    fun exportModelToDownloads(model: LocalModel, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = importer.exportModelToDownloads(getApplication(), model.file)
            result.onSuccess { file ->
                onResult(true, "Modèle '${model.name}' exporté avec succès vers :\n${file.absolutePath}")
            }.onFailure { err ->
                onResult(false, "Échec de l'exportation : ${err.localizedMessage}")
            }
        }
    }

    fun exportModelToUri(model: LocalModel, destinationUri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = importer.exportModelToUri(getApplication(), model.file, destinationUri)
            result.onSuccess {
                onResult(true, "Modèle '${model.name}' exporté avec succès.")
            }.onFailure { err ->
                onResult(false, "Échec de l'exportation : ${err.localizedMessage}")
            }
        }
    }

    fun importModelFromPath(filePath: String, onResult: (Boolean, String) -> Unit) {
        val cleanPath = filePath.trim()
        if (cleanPath.isBlank()) {
            onResult(false, "Chemin de fichier vide.")
            return
        }
        viewModelScope.launch {
            val file = File(cleanPath)
            _loadingState.value = LoadingState.Importing(fileName = file.name, progress = 0f)
            try {
                val copiedFile = importer.importFromLocalPath(
                    context = getApplication(),
                    filePath = cleanPath,
                    onProgress = { progress ->
                        _loadingState.value = LoadingState.Importing(fileName = file.name, progress = progress)
                    }
                )
                refreshLocalModels()
                initializeEngineWithModel(copiedFile.nameWithoutExtension, copiedFile.absolutePath)
                onResult(true, "Modèle importé avec succès.")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur importation depuis chemin", e)
                _loadingState.value = LoadingState.Error("Échec de l'importation : ${e.localizedMessage}")
                onResult(false, e.localizedMessage ?: "Erreur inconnue")
            }
        }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val localFile = mediaProcessor.saveImageLocally(getApplication(), uri)
                _attachedImagePath.value = localFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Erreur enregistrement image", e)
            }
        }
    }

    fun clearAttachedImage() {
        _attachedImagePath.value = null
    }

    fun generateAsciiFromAttachedImage() {
        val path = _attachedImagePath.value ?: return
        viewModelScope.launch {
            val file = File(path)
            val asciiArt = mediaProcessor.convertImageToAscii(file, maxWidth = 48)
            val asciiMessage = ChatMessage(
                text = "🎨 **Rendu Multimédia On-Device (ASCII Art)** :\n```\n$asciiArt\n```",
                isUser = false,
                isMultimediaArtifact = true,
                artifactType = "ASCII"
            )
            _messages.update { it + asciiMessage }
            saveCurrentSession()
        }
    }

    /**
     * Envoie un message et collecte le flux de tokens avec fluidité optimisée
     * (batching des micro-tokens pour éviter la surcharge de recomposition).
     */
    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        val imagePath = _attachedImagePath.value

        if (trimmed.isEmpty() && imagePath == null) return
        if (_isGenerating.value) return
        if (_loadingState.value !is LoadingState.Ready) return

        val effectivePrompt = trimmed.ifBlank { "Analyse cette image ci-jointe en détail." }

        val userMessage = ChatMessage(
            text = effectivePrompt,
            isUser = true,
            imagePath = imagePath
        )
        _messages.update { it + userMessage }

        val assistantMessageId = UUID.randomUUID().toString()
        val placeholderAssistant = ChatMessage(
            id = assistantMessageId,
            text = "",
            isUser = false,
            isStreaming = true
        )
        _messages.update { it + placeholderAssistant }

        _attachedImagePath.value = null
        _isGenerating.value = true

        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        currentInferenceJob = viewModelScope.launch {
            try {
                var pendingBuffer = StringBuilder()
                var lastUiUpdateTime = System.currentTimeMillis()

                llmEngine.chat(effectivePrompt, imagePath).collect { chunk ->
                    tokenCount++
                    pendingBuffer.append(chunk)

                    val now = System.currentTimeMillis()
                    // Mise à jour de l'UI par batchs de ~25ms pour une fluidité parfaite à 60/120 FPS
                    if (now - lastUiUpdateTime >= 25 || chunk.contains("\n")) {
                        val textToAppend = pendingBuffer.toString()
                        pendingBuffer = StringBuilder()
                        lastUiUpdateTime = now

                        val elapsedSec = (now - startTime) / 1000f
                        val tps = if (elapsedSec > 0.3f) tokenCount / elapsedSec else null

                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        text = msg.text + textToAppend,
                                        isStreaming = true,
                                        tokenSpeed = tps
                                    )
                                } else {
                                    msg
                                }
                            }
                        }
                    }
                }

                // Vidage du reste du buffer si nécessaire
                if (pendingBuffer.isNotEmpty()) {
                    val remaining = pendingBuffer.toString()
                    _messages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMessageId) {
                                msg.copy(text = msg.text + remaining)
                            } else msg
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erreur streaming", e)
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(
                                text = msg.text + "\n[Fin de réponse]",
                                isStreaming = false
                            )
                        } else msg
                    }
                }
            } finally {
                _isGenerating.value = false
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(
                                text = msg.text.ifEmpty { "Pas de réponse produite par le modèle." },
                                isStreaming = false
                            )
                        } else msg
                    }
                }
                saveCurrentSession()
            }
        }
    }

    fun stopGeneration() {
        currentInferenceJob?.cancel()
        _isGenerating.value = false
        _messages.update { list ->
            list.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        }
        saveCurrentSession()
    }

    fun deleteLocalModel(fileName: String) {
        viewModelScope.launch {
            if (_selectedModelName.value == fileName.removeSuffix(".litertlm")) {
                llmEngine.close()
                _loadingState.value = LoadingState.Idle
                _selectedModelName.value = null
            }
            downloader.deleteModel(getApplication(), fileName)
            refreshLocalModels()
        }
    }

    fun clearChat() {
        llmEngine.resetConversation()
        _messages.value = emptyList()
        saveCurrentSession()
    }

    override fun onCleared() {
        super.onCleared()
        currentInferenceJob?.cancel()
        currentDownloadJob?.cancel()
        currentInitJob?.cancel()
        llmEngine.close()
        Log.i(TAG, "ChatViewModel nettoyé.")
    }
}
