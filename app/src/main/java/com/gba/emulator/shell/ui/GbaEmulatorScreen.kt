package com.gba.emulator.shell.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gba.emulator.shell.BridgeSelfTest
import com.gba.emulator.shell.BuildConfig
import com.gba.emulator.shell.CartridgeMetadata
import com.gba.emulator.shell.EmulatorSession
import com.gba.emulator.shell.GbaRuntimeBridge
import com.gba.emulator.shell.PersistenceSelfTest
import com.gba.emulator.shell.PushedRomLoader
import com.gba.emulator.shell.R
import com.gba.emulator.shell.RomLoader
import com.gba.emulator.shell.RomValidator
import com.gba.emulator.shell.RomVideoSelfTest
import com.gba.emulator.shell.RuntimeSelfTest
import com.gba.emulator.shell.SaveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LocalContextGetResourceValueCall")
@Composable
fun GbaEmulatorScreen(
    session: EmulatorSession,
    darkModeOverride: String,
    onDarkModeOverrideChanged: (String) -> Unit,
    onPlayRom: (RomLoader.LoadedRom) -> Unit,
    onResumePlay: () -> Unit,
) {
    val context = LocalContext.current
    val readyMessage = stringResource(R.string.status_ready)
    val passFormat = stringResource(R.string.status_pass)
    val failFormat = stringResource(R.string.status_fail)
    val passFramebufferFormat = stringResource(R.string.status_pass_framebuffer)
    val noSaveDataMessage = stringResource(R.string.status_no_save_data)
    val saveExportedFormat = stringResource(R.string.status_save_exported)
    val saveImportedFormat = stringResource(R.string.status_save_imported)
    val saveStateExportedFormat = stringResource(R.string.status_save_state_exported)
    val saveStateLoadedFormat = stringResource(R.string.status_save_state_loaded)
    val persistenceIoFailedFormat = stringResource(R.string.status_persistence_io_failed)
    val persistenceImportFailedFormat = stringResource(R.string.status_persistence_import_failed)
    val noRomLoadedMessage = stringResource(R.string.status_no_rom_loaded)
    val scope = rememberCoroutineScope()

    var statusText by remember(readyMessage) { mutableStateOf(readyMessage) }
    var running by remember { mutableStateOf(false) }
    var romLoaded by remember { mutableStateOf(false) }
    var lastMetadata by remember { mutableStateOf<CartridgeMetadata?>(null) }
    var suggestedSaveName by remember { mutableStateOf("game.sav") }
    var suggestedStateName by remember { mutableStateOf("game.gbss") }
    var framebufferBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var devToolsExpanded by remember { mutableStateOf(false) }

    val saveHintLabels = RomValidator.SaveTypeHint.entries.associateWith { hint ->
        stringResource(saveTypeHintLabelRes(hint))
    }

    fun applyLoadedRom(loaded: RomLoader.LoadedRom) {
        romLoaded = true
        val baseName = loaded.displayName.substringBeforeLast('.').ifEmpty { "game" }
        suggestedSaveName = "$baseName.sav"
        suggestedStateName = "$baseName.gbss"
        lastMetadata = session.getCartridgeMetadata()
        val saveHint = saveHintLabels[loaded.validation.saveTypeHint] ?: loaded.validation.saveTypeHint.name
        statusText = context.getString(
            R.string.status_rom_loaded,
            loaded.title,
            loaded.sizeBytes,
            saveHint,
        )
        onPlayRom(loaded)
    }

    fun loadRomBytes(loadResult: Result<RomLoader.LoadedRom>) {
        running = true
        framebufferBitmap = null
        statusText = context.getString(R.string.status_rom_loading)
        scope.launch {
            try {
                if (!isActive) {
                    return@launch
                }
                loadResult.fold(
                    onSuccess = { loaded ->
                        val status = withContext(Dispatchers.Default) {
                            session.loadRom(loaded)
                        }
                        if (!isActive) {
                            return@fold
                        }
                        if (status == GbaRuntimeBridge.RuntimeStatus.Ok) {
                            withContext(Dispatchers.Main) {
                                applyLoadedRom(loaded)
                            }
                        } else {
                            romLoaded = false
                            lastMetadata = null
                            statusText = context.getString(R.string.status_rom_rejected)
                        }
                    },
                    onFailure = { error ->
                        statusText = context.getString(
                            R.string.status_rom_load_failed,
                            error.message ?: "unknown",
                        )
                    },
                )
            } finally {
                running = false
            }
        }
    }

    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            running = true
            try {
                val bytes = withContext(Dispatchers.Default) { session.exportCartridgeSave() }
                if (bytes == null || bytes.isEmpty()) {
                    statusText = noSaveDataMessage
                    return@launch
                }
                val writeResult = withContext(Dispatchers.IO) {
                    SaveRepository.writeBytesWithBackup(context, uri, bytes)
                }
                writeResult.fold(
                    onSuccess = {
                        statusText = saveExportedFormat.format(bytes.size, SaveRepository.sha256Hex(bytes))
                    },
                    onFailure = { error ->
                        statusText = persistenceIoFailedFormat.format(error.message ?: "unknown")
                    },
                )
            } finally {
                running = false
            }
        }
    }

    val importSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            running = true
            try {
                val readResult = withContext(Dispatchers.IO) { SaveRepository.readAllBytes(context, uri) }
                readResult.fold(
                    onSuccess = { bytes ->
                        val importStatus = withContext(Dispatchers.Default) {
                            session.importCartridgeSave(bytes)
                        }
                        if (importStatus == GbaRuntimeBridge.PersistenceStatus.Ok) {
                            statusText = saveImportedFormat.format(
                                bytes.size,
                                SaveRepository.sha256Hex(bytes),
                            )
                        } else {
                            statusText = persistenceImportFailedFormat.format(importStatus.name)
                        }
                    },
                    onFailure = { error ->
                        statusText = persistenceIoFailedFormat.format(error.message ?: "unknown")
                    },
                )
            } finally {
                running = false
            }
        }
    }

    val exportSaveStateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            running = true
            try {
                val bytes = withContext(Dispatchers.Default) { session.encodeSaveState() }
                if (bytes == null || bytes.isEmpty()) {
                    statusText = noSaveDataMessage
                    return@launch
                }
                val writeResult = withContext(Dispatchers.IO) {
                    SaveRepository.writeBytesWithBackup(context, uri, bytes)
                }
                writeResult.fold(
                    onSuccess = {
                        statusText = saveStateExportedFormat.format(bytes.size)
                    },
                    onFailure = { error ->
                        statusText = persistenceIoFailedFormat.format(error.message ?: "unknown")
                    },
                )
            } finally {
                running = false
            }
        }
    }

    val importSaveStateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            running = true
            try {
                val readResult = withContext(Dispatchers.IO) { SaveRepository.readAllBytes(context, uri) }
                readResult.fold(
                    onSuccess = { bytes ->
                        val decodeStatus = withContext(Dispatchers.Default) {
                            session.loadSaveState(bytes)
                        }
                        if (decodeStatus == GbaRuntimeBridge.SaveStateDecodeStatus.Ok) {
                            statusText = saveStateLoadedFormat.format(decodeStatus.name)
                        } else {
                            statusText = persistenceImportFailedFormat.format(decodeStatus.name)
                        }
                    },
                    onFailure = { error ->
                        statusText = persistenceIoFailedFormat.format(error.message ?: "unknown")
                    },
                )
            } finally {
                running = false
            }
        }
    }

    val openRomLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        scope.launch {
            val loadResult = withContext(Dispatchers.IO) {
                if (!isActive) {
                    return@withContext Result.failure(IllegalStateException("cancelled"))
                }
                RomLoader.load(context, uri)
            }
            if (isActive) {
                loadRomBytes(loadResult)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
            horizontalAlignment = Alignment.Start,
        ) {
            // Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = FlowframeColors.SkyNeon.copy(alpha = 0.2f),
                                contentColor = FlowframeColors.SkyNeon,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v0.1.0-dev",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Dark Mode Toggle
                    IconButton(
                        onClick = {
                            val nextMode = when (darkModeOverride) {
                                "system" -> "light"
                                "light" -> "dark"
                                else -> "system"
                            }
                            onDarkModeOverrideChanged(nextMode)
                        }
                    ) {
                        val icon = when (darkModeOverride) {
                            "light" -> Icons.Default.LightMode
                            "dark" -> Icons.Default.DarkMode
                            else -> Icons.Default.Settings
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle Dark Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Cartridge Slot / Loaded Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                if (running) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else if (!romLoaded) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.insert_rom_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    openRomLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.open_rom))
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GAME BOY ADVANCE CARTRIDGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                lastMetadata?.let { metadata ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = stringResource(saveTypeLabelRes(metadata.saveType)),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = lastMetadata?.title ?: "Unknown Game",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                lastMetadata?.let { metadata ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "CODE: ${metadata.gameCode}",
                                            style = flowframeMetadataStyle(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "·",
                                            style = flowframeMetadataStyle(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "MAKER: ${metadata.makerCode}",
                                            style = flowframeMetadataStyle(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Button(
                                onClick = {
                                    onResumePlay()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FlowframeColors.SkyNeon,
                                    contentColor = FlowframeColors.OnSkyNeon
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.play_game),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (romLoaded && !running) {
                OutlinedButton(
                    onClick = {
                        openRomLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Change ROM Cartridge")
                }
            }

            // Saves & States Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.saves_and_states),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cartridge Backup (.sav)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Export/Import direct game saves",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                enabled = romLoaded && !running,
                                onClick = {
                                    exportSaveLauncher.launch(suggestedSaveName)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Export Save File",
                                    tint = if (romLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                enabled = romLoaded && !running,
                                onClick = {
                                    importSaveLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = "Import Save File",
                                    tint = if (romLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Emulator Save State (.gbss)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Instant freeze-frame state backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                enabled = romLoaded && !running,
                                onClick = {
                                    exportSaveStateLauncher.launch(suggestedStateName)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Export Save State",
                                    tint = if (romLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                enabled = romLoaded && !running,
                                onClick = {
                                    importSaveStateLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = "Import Save State",
                                    tint = if (romLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Boot Card (Debug)
            if (BuildConfig.DEBUG && !running) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Quick Boot (Debug)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            enabled = PushedRomLoader.hasPushedRom(context),
                            onClick = {
                                loadRomBytes(PushedRomLoader.load(context))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.load_pushed_rom))
                        }
                        Text(
                            text = stringResource(R.string.load_pushed_rom_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.privacy_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.privacy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = { devToolsExpanded = !devToolsExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (devToolsExpanded) {
                                R.string.dev_tools_collapse
                            } else {
                                R.string.dev_tools_expand
                            },
                        ),
                    )
                }

                AnimatedVisibility(visible = devToolsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        framebufferBitmap?.let { bitmap ->
                            GameViewportFrame(
                                screenWidth = GbaRuntimeBridge.SCREEN_WIDTH,
                                screenHeight = GbaRuntimeBridge.SCREEN_HEIGHT,
                            ) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = stringResource(
                                        R.string.framebuffer_content_description,
                                    ),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.None,
                                )
                            }
                        }

                        OutlinedButton(
                            enabled = !running,
                            onClick = {
                                running = true
                                framebufferBitmap = null
                                statusText = context.getString(R.string.status_bridge_running)
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) { BridgeSelfTest.run() }
                                    statusText = if (result.passed) {
                                        passFormat.format(result.summary)
                                    } else {
                                        failFormat.format(result.summary)
                                    }
                                    running = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.run_bridge_self_test))
                        }

                        OutlinedButton(
                            enabled = !running,
                            onClick = {
                                running = true
                                framebufferBitmap = null
                                statusText = context.getString(R.string.status_persistence_running)
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) { PersistenceSelfTest.run() }
                                    statusText = if (result.passed) {
                                        passFormat.format(result.summary)
                                    } else {
                                        failFormat.format(result.summary)
                                    }
                                    running = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.run_persistence_self_test))
                        }

                        OutlinedButton(
                            enabled = !running,
                            onClick = {
                                running = true
                                framebufferBitmap = null
                                statusText = context.getString(R.string.status_rom_video_running)
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        RomVideoSelfTest.run(context)
                                    }
                                    statusText = when {
                                        result.skipped -> {
                                            context.getString(R.string.status_rom_video_skipped, result.summary)
                                        }
                                        result.passed -> passFormat.format(result.summary)
                                        else -> failFormat.format(result.summary)
                                    }
                                    running = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.run_rom_video_self_test))
                        }

                        OutlinedButton(
                            enabled = !running,
                            onClick = {
                                running = true
                                framebufferBitmap = null
                                statusText = context.getString(R.string.status_runtime_running)
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) { RuntimeSelfTest.run() }
                                    if (result.passed && result.framebufferRgb565 != null) {
                                        framebufferBitmap =
                                            Rgb565Framebuffer.toImageBitmap(result.framebufferRgb565)
                                        statusText = passFramebufferFormat.format(result.summary)
                                    } else if (result.passed) {
                                        statusText = passFormat.format(result.summary)
                                    } else {
                                        statusText = failFormat.format(result.summary)
                                    }
                                    running = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.run_runtime_self_test))
                        }
                    }
                }
            }
        }
    }
}
