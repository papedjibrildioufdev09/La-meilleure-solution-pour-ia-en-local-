package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.AppThemeMode
import com.example.model.BenchmarkResult
import com.example.model.ChatMessage
import com.example.model.ConversationSession
import com.example.model.LoadingState
import com.example.model.LocalModel
import com.example.model.MessageColorStyle
import com.example.model.ModelCatalog
import com.example.model.PresetModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val localModels by viewModel.localModels.collectAsStateWithLifecycle()
    val selectedModelName by viewModel.selectedModelName.collectAsStateWithLifecycle()
    val attachedImagePath by viewModel.attachedImagePath.collectAsStateWithLifecycle()
    val showSetupDialog by viewModel.showSetupDialog.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val messageColorStyle by viewModel.messageColorStyle.collectAsStateWithLifecycle()
    val hfToken by viewModel.hfToken.collectAsStateWithLifecycle()
    val forceCpu by viewModel.forceCpu.collectAsStateWithLifecycle()

    // Historique et Benchmark
    val savedSessions by viewModel.savedSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val isBenchmarking by viewModel.isBenchmarking.collectAsStateWithLifecycle()
    val benchmarkStatus by viewModel.benchmarkStatus.collectAsStateWithLifecycle()
    val latestBenchmarkResult by viewModel.latestBenchmarkResult.collectAsStateWithLifecycle()
    val benchmarkHistory by viewModel.benchmarkHistory.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBenchmarkDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showCustomDownloadDialog by remember { mutableStateOf(false) }
    var showPathImportDialog by remember { mutableStateOf(false) }
    var modelToExport by remember { mutableStateOf<LocalModel?>(null) }
    var showExportOptionsForModel by remember { mutableStateOf<LocalModel?>(null) }

    val isModelReady = loadingState is LoadingState.Ready

    // Gestion de retour physique Android
    BackHandler(enabled = showModelSheet || showHistorySheet || showBenchmarkDialog || showExportOptionsForModel != null || showPathImportDialog) {
        showModelSheet = false
        showHistorySheet = false
        showBenchmarkDialog = false
        showExportOptionsForModel = null
        showPathImportDialog = false
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importCustomModel(it)
            showModelSheet = false
        }
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val model = modelToExport
        if (uri != null && model != null) {
            viewModel.exportModelToUri(model, uri) { _, msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachImage(it) }
    }

    val listState = rememberLazyListState()

    // Défilement automatique lors de la réception des tokens
    LaunchedEffect(messages.lastOrNull()?.text, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LiteRT AI",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = messageColorStyle.containerColor,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = "Hors ligne",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "100% Hors-Ligne",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (val state = loadingState) {
                                is LoadingState.Ready -> "${state.modelName} • Accélération ${state.backendName}"
                                is LoadingState.Initializing -> "Initialisation en cours (${state.backendName})..."
                                is LoadingState.Downloading -> "Téléchargement : ${state.modelName}"
                                is LoadingState.Importing -> "Importation de ${state.fileName}..."
                                is LoadingState.Error -> "Erreur de chargement"
                                LoadingState.Idle -> "En attente de modèle"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Historique des discussions
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historique des discussions"
                        )
                    }
                    // Benchmark On-Device
                    IconButton(
                        onClick = { showBenchmarkDialog = true },
                        modifier = Modifier.testTag("benchmark_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Benchmark hors-ligne"
                        )
                    }
                    // Thème et couleurs
                    IconButton(
                        onClick = { viewModel.openSetupDialog() },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Configuration thème et modèle"
                        )
                    }
                    // Gestionnaire de modèles
                    IconButton(
                        onClick = { showModelSheet = true },
                        modifier = Modifier.testTag("model_manager_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Gestionnaire de modèles"
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Plus d'options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nouvelle discussion") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showMenu = false
                                viewModel.createNewSession()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Historique des discussions") },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = {
                                showMenu = false
                                showHistorySheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lancer Benchmark On-Device") },
                            leadingIcon = { Icon(Icons.Default.Speed, null) },
                            onClick = {
                                showMenu = false
                                showBenchmarkDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Gérer les modèles") },
                            leadingIcon = { Icon(Icons.Default.Tune, null) },
                            onClick = {
                                showMenu = false
                                showModelSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dépôt Hugging Face libre") },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = {
                                showMenu = false
                                showCustomDownloadDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Effacer cette discussion") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                showMenu = false
                                viewModel.clearChat()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            PersistentLoadingBanner(
                loadingState = loadingState,
                onCancelDownload = { viewModel.cancelDownload() },
                onCancelInitialization = { viewModel.cancelInitialization() },
                onOpenModelManager = { showModelSheet = true }
            )

            // Zone centrale des messages
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = messageColorStyle.containerColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = messageColorStyle.primaryColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isModelReady) "Modèle prêt • Prêt pour vos questions" else "Chargement du modèle On-Device...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isModelReady) {
                                "Vos discussions et images restent strictement sur cet appareil (zéro appel réseau)."
                            } else {
                                "Préparation du moteur d'inférence locale..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (isModelReady) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { showBenchmarkDialog = true },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tester les performances (Benchmark)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            userBubbleColor = messageColorStyle.primaryColor,
                            userTextColor = messageColorStyle.onPrimaryColor
                        )
                    }
                }
            }

            // Aperçu de l'image attachée si présente
            attachedImagePath?.let { path ->
                AttachmentPreviewCard(
                    imagePath = path,
                    accentColor = messageColorStyle.primaryColor,
                    onRemove = { viewModel.clearAttachedImage() },
                    onGenerateAscii = { viewModel.generateAsciiFromAttachedImage() }
                )
            }

            // Barre de saisie inférieure
            ChatInputBar(
                text = inputText,
                onTextChanged = { inputText = it },
                isGenerating = isGenerating,
                isModelReady = isModelReady,
                primaryColor = messageColorStyle.primaryColor,
                onSendMessage = {
                    if (inputText.isNotBlank() || attachedImagePath != null) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                onStopGeneration = { viewModel.stopGeneration() },
                onAttachPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    }

    // Feuille modale de l'Historique des discussions
    if (showHistorySheet) {
        ConversationHistoryBottomSheet(
            sessions = savedSessions,
            currentSessionId = currentSessionId,
            accentColor = messageColorStyle.primaryColor,
            onDismiss = { showHistorySheet = false },
            onNewSession = {
                viewModel.createNewSession()
                showHistorySheet = false
            },
            onSelectSession = { session ->
                viewModel.loadSession(session)
                showHistorySheet = false
            },
            onDeleteSession = { sessionId ->
                viewModel.deleteSession(sessionId)
            }
        )
    }

    // Dialogue du Benchmark On-Device
    if (showBenchmarkDialog) {
        OfflineBenchmarkDialog(
            isBenchmarking = isBenchmarking,
            benchmarkStatus = benchmarkStatus,
            latestResult = latestBenchmarkResult,
            history = benchmarkHistory,
            selectedModelName = selectedModelName,
            isModelReady = isModelReady,
            accentColor = messageColorStyle.primaryColor,
            onDismiss = { showBenchmarkDialog = false },
            onStartBenchmark = { viewModel.startOfflineBenchmark() }
        )
    }

    // Configuration initiale
    if (showSetupDialog) {
        InitialSetupDialog(
            currentTheme = themeMode,
            currentColor = messageColorStyle,
            currentHfToken = hfToken,
            initialForceCpu = forceCpu,
            onDismiss = { viewModel.closeSetupDialog() },
            onConfirm = { chosenPreset, chosenTheme, chosenColor, token, cpuMode ->
                viewModel.completeInitialSetup(chosenPreset, chosenTheme, chosenColor, token, cpuMode)
            },
            onImportLocalModel = {
                viewModel.closeSetupDialog()
                modelPickerLauncher.launch(arrayOf("*/*"))
            }
        )
    }

    // Gestionnaire de Modèles
    if (showModelSheet) {
        ModelManagerBottomSheet(
            localModels = localModels,
            selectedModelName = selectedModelName,
            onDismiss = { showModelSheet = false },
            onSelectModel = { model ->
                viewModel.initializeEngineWithModel(model.name, model.file.absolutePath)
                showModelSheet = false
            },
            onDeleteModel = { model ->
                viewModel.deleteLocalModel(model.file.name)
            },
            onExportModel = { model ->
                showExportOptionsForModel = model
            },
            onImportLocalFile = {
                modelPickerLauncher.launch(arrayOf("*/*"))
            },
            onOpenPathImport = {
                showPathImportDialog = true
            },
            onDownloadPreset = { preset ->
                viewModel.downloadPresetModel(preset)
                showModelSheet = false
            },
            onOpenCustomDownload = {
                showModelSheet = false
                showCustomDownloadDialog = true
            }
        )
    }

    // Dialogue des options d'exportation de modèle externe
    showExportOptionsForModel?.let { model ->
        AlertDialog(
            onDismissRequest = { showExportOptionsForModel = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = messageColorStyle.primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporter le modèle")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sélectionnez la destination d'exportation pour '${model.name}' (${model.sizeFormatted}) :",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            val targetModel = model
                            showExportOptionsForModel = null
                            viewModel.exportModelToDownloads(targetModel) { _, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dossier Téléchargements (/sdcard/Download)")
                    }
                    OutlinedButton(
                        onClick = {
                            val targetModel = model
                            modelToExport = targetModel
                            showExportOptionsForModel = null
                            exportDocumentLauncher.launch("${targetModel.name}.litertlm")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sélecteur de fichier Android (SAF)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportOptionsForModel = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialogue d'importation par chemin absolu
    if (showPathImportDialog) {
        var pathText by remember { mutableStateOf("/sdcard/Download/") }
        AlertDialog(
            onDismissRequest = { showPathImportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = messageColorStyle.primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importer par chemin direct")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Indiquez le chemin absolu du fichier .litertlm sur la mémoire de votre téléphone :",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pathText,
                        onValueChange = { pathText = it },
                        label = { Text("Chemin du fichier") },
                        placeholder = { Text("/sdcard/Download/model.litertlm") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = pathText
                        showPathImportDialog = false
                        showModelSheet = false
                        viewModel.importModelFromPath(path) { _, msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                ) {
                    Text("Importer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPathImportDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dépôt Hugging Face personnalisé
    if (showCustomDownloadDialog) {
        CustomDownloadDialog(
            onDismiss = { showCustomDownloadDialog = false },
            onConfirm = { repo, file ->
                viewModel.downloadCustomHuggingFaceModel(repo, file)
                showCustomDownloadDialog = false
            }
        )
    }
}

/**
 * Feuille modale de l'Historique des discussions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryBottomSheet(
    sessions: List<ConversationSession>,
    currentSessionId: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (ConversationSession) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historique des discussions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = onNewSession,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nouvelle")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (sessions.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune discussion précédente enregistrée. Vos futures conversations apparaîtront ici automatiquement.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isCurrent = session.id == currentSessionId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSession(session) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, accentColor) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${dateFormat.format(Date(session.updatedAt))} • ${session.messages.size} messages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteSession(session.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer la discussion",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialogue de Benchmark On-Device
 */
@Composable
fun OfflineBenchmarkDialog(
    isBenchmarking: Boolean,
    benchmarkStatus: String,
    latestResult: BenchmarkResult?,
    history: List<BenchmarkResult>,
    selectedModelName: String?,
    isModelReady: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onStartBenchmark: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Benchmark On-Device",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Mesure la vitesse d'inférence réelle en local sur votre appareil (sans Internet) :",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                item {
                    if (isBenchmarking) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = benchmarkStatus,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartBenchmark,
                            enabled = isModelReady,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isModelReady) "Lancer le test de performance" else "Chargez d'abord un modèle")
                        }
                    }
                }

                // Dernier résultat
                latestResult?.let { res ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = res.modelName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = res.rating,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = String.format("%.1f tok/s", res.decodeTokensPerSecond),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor
                                            )
                                        )
                                        Text("Vitesse Décodage", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column {
                                        Text(
                                            text = "${res.ttftMs} ms",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text("Latence (TTFT)", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column {
                                        Text(
                                            text = res.backend,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text("Backend", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Historique des benchmarks
                if (history.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Historique des mesures (${history.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    items(history) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${item.modelName} (${item.backend})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = dateFormat.format(Date(item.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = String.format("%.1f tok/s", item.decodeTokensPerSecond),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

/**
 * Indicateur de chargement persistant
 */
@Composable
fun PersistentLoadingBanner(
    loadingState: LoadingState,
    onCancelDownload: () -> Unit,
    onCancelInitialization: () -> Unit,
    onOpenModelManager: () -> Unit
) {
    AnimatedVisibility(visible = loadingState !is LoadingState.Ready) {
        when (loadingState) {
            is LoadingState.Downloading -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress = { loadingState.progress },
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Téléchargement : ${loadingState.modelName}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            TextButton(onClick = onCancelDownload) {
                                Text("Annuler")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { loadingState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val percent = (loadingState.progress * 100).toInt()
                            val downloadedMb = loadingState.downloadedBytes / (1024 * 1024)
                            val totalMb = loadingState.totalBytes / (1024 * 1024)
                            Text(
                                text = "$percent% • $downloadedMb Mo / $totalMb Mo",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = loadingState.speedText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
            is LoadingState.Importing -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Importation du modèle local...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = loadingState.fileName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            is LoadingState.Initializing -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Initialisation On-Device...",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${loadingState.modelName} • ${loadingState.backendName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            TextButton(onClick = onCancelInitialization) {
                                Text("Annuler")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Chargement des poids du modèle en RAM. Sur Android 8/9, cela peut prendre 20 à 40s.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            is LoadingState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Erreur de modèle",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = loadingState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onOpenModelManager) {
                                Text("Choisir un autre modèle", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
            LoadingState.Idle -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenModelManager() }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aucun modèle chargé — Appuyez pour configurer",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        Text(
                            text = "Configurer",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun InitialSetupDialog(
    currentTheme: AppThemeMode,
    currentColor: MessageColorStyle,
    currentHfToken: String,
    initialForceCpu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (preset: PresetModel?, theme: AppThemeMode, color: MessageColorStyle, hfToken: String, forceCpu: Boolean) -> Unit,
    onImportLocalModel: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf<PresetModel?>(ModelCatalog.PRESETS.first()) }
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var selectedColor by remember { mutableStateOf(currentColor) }
    var hfTokenText by remember { mutableStateOf(currentHfToken) }
    var forceCpuSelected by remember { mutableStateOf(initialForceCpu) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configuration de LiteRT AI",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "1. Choisissez un modèle d'IA local",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                items(ModelCatalog.PRESETS) { preset ->
                    val isChecked = selectedPreset?.id == preset.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPreset = preset },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "~${preset.estimatedSizeMb} Mo • RAM : ${preset.recommendedRam}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onImportLocalModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ou importer mon propre .litertlm")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Thème de l'application",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedTheme == mode,
                                onClick = { selectedTheme = mode },
                                label = { Text(mode.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3. Couleur des bulles de discussion",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MessageColorStyle.entries.forEach { colorStyle ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorStyle.primaryColor)
                                    .border(
                                        width = if (selectedColor == colorStyle) 3.dp else 1.dp,
                                        color = if (selectedColor == colorStyle) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorStyle },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorStyle) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "4. Accélération matérielle",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (forceCpuSelected) "Mode CPU (Recommandé)" else "Mode GPU",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (forceCpuSelected) {
                                        "Recommandé sur Android 8/9 pour une stabilité totale sans blocage des pilotes graphiques."
                                    } else {
                                        "Pour smartphones Android 10+ avec support GPU Vulkan 1.1+."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = !forceCpuSelected,
                                onCheckedChange = { forceCpuSelected = !it }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Jeton Hugging Face (Optionnel)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = hfTokenText,
                        onValueChange = { hfTokenText = it },
                        placeholder = { Text("hf_... (uniquement si modèle sous licence)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedPreset, selectedTheme, selectedColor, hfTokenText, forceCpuSelected)
                }
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    userBubbleColor: Color,
    userTextColor: Color
) {
    val clipboardManager = LocalClipboardManager.current
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = userBubbleColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LiteRT On-Device",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = userBubbleColor
                    )
                    if (message.tokenSpeed != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f tok/s", message.tokenSpeed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) userBubbleColor else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    message.imagePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Image du message",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(bottom = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (message.isStreaming && message.text.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            TypingDotsIndicator(dotColor = userBubbleColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Inférence locale en cours...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val isAscii = message.isMultimediaArtifact && message.artifactType == "ASCII"
                        Text(
                            text = message.text,
                            style = if (isAscii) {
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = if (isUser) userTextColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isUser && message.text.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Copier",
                                style = MaterialTheme.typography.labelSmall,
                                color = userBubbleColor,
                                modifier = Modifier
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(message.text))
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingDotsIndicator(dotColor: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    isGenerating: Boolean,
    isModelReady: Boolean,
    primaryColor: Color,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onAttachPhoto: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAttachPhoto,
                enabled = isModelReady && !isGenerating,
                modifier = Modifier.testTag("attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Joindre une image",
                    tint = if (isModelReady) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                enabled = isModelReady && !isGenerating,
                placeholder = {
                    Text(if (isModelReady) "Posez votre question à l'IA locale..." else "Modèle en cours de chargement...")
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("message_input"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            if (isGenerating) {
                IconButton(
                    onClick = onStopGeneration,
                    modifier = Modifier
                        .testTag("stop_button")
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Arrêter l'inférence",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                IconButton(
                    onClick = onSendMessage,
                    enabled = isModelReady && text.isNotBlank(),
                    modifier = Modifier
                        .testTag("send_button")
                        .background(
                            if (isModelReady && text.isNotBlank()) primaryColor else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Envoyer",
                        tint = if (isModelReady && text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentPreviewCard(
    imagePath: String,
    accentColor: Color,
    onRemove: () -> Unit,
    onGenerateAscii: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Image attachée",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Média attaché",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = File(imagePath).name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                    onClick = onGenerateAscii,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Générer Art ASCII", fontSize = 11.sp)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Retirer le média")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerBottomSheet(
    localModels: List<LocalModel>,
    selectedModelName: String?,
    onDismiss: () -> Unit,
    onSelectModel: (LocalModel) -> Unit,
    onDeleteModel: (LocalModel) -> Unit,
    onExportModel: (LocalModel) -> Unit,
    onImportLocalFile: () -> Unit,
    onOpenPathImport: () -> Unit,
    onDownloadPreset: (PresetModel) -> Unit,
    onOpenCustomDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Gestionnaire de Modèles On-Device",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Modèles exécutés en local sans réseau. Stockage interne de l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onImportLocalFile,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_model_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Parcourir (.litertlm)", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenPathImport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("path_import_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chemin direct (/sdcard)", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Modèles installés (${localModels.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (localModels.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucun modèle n'est encore installé. Choisissez un modèle Hugging Face vérifié ci-dessous.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    localModels.forEach { model ->
                        val isSelected = selectedModelName == model.name
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(model) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${model.sizeFormatted} • ${if (model.isCustomImport) "Import local" else "Hugging Face"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onExportModel(model) }) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Exporter le modèle vers le stockage externe",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteModel(model) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer le modèle",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modèles Hugging Face Publics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onOpenCustomDownload) {
                    Text("Dépôt libre")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelCatalog.PRESETS.forEach { preset ->
                    val isAlreadyInstalled = localModels.any { it.file.name == preset.fileName }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Taille : ~${preset.estimatedSizeMb} Mo • RAM : ${preset.recommendedRam}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isAlreadyInstalled) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Installé",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { onDownloadPreset(preset) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Télécharger")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (repoId: String, fileName: String) -> Unit
) {
    var repoId by remember { mutableStateOf("litert-community/Qwen3.5-0.8B") }
    var fileName by remember { mutableStateOf("Qwen3.5-0.8B_int8.litertlm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Téléchargement Hugging Face personnalisé") },
        text = {
            Column {
                Text(
                    text = "Spécifiez le dépôt Hugging Face et le nom du fichier .litertlm :",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = repoId,
                    onValueChange = { repoId = it },
                    label = { Text("ID Dépôt (ex: litert-community/...)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nom du fichier (ex: model.litertlm)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(repoId, fileName) }) {
                Text("Télécharger")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
