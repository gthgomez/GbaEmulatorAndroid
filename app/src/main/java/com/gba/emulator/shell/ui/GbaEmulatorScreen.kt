package com.gba.emulator.shell.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.gba.emulator.shell.RuntimeSelfTest
import com.gba.emulator.shell.SaveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GbaEmulatorScreen(
    session: EmulatorSession,
    onPlayRom: (RomLoader.LoadedRom) -> Unit,
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
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                enabled = !running,
                onClick = {
                    openRomLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_rom))
            }

            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    enabled = !running && PushedRomLoader.hasPushedRom(context),
                    onClick = {
                        loadRomBytes(PushedRomLoader.load(context))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.load_pushed_rom))
                }
                Text(
                    text = stringResource(R.string.load_pushed_rom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            lastMetadata?.let { metadata ->
                Text(
                    text = stringResource(
                        R.string.status_rom_metadata,
                        metadata.title,
                        metadata.gameCode,
                        metadata.makerCode,
                        stringResource(saveTypeLabelRes(metadata.saveType)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.saves_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                enabled = !running && romLoaded,
                onClick = {
                    if (!romLoaded) {
                        statusText = noRomLoadedMessage
                        return@OutlinedButton
                    }
                    exportSaveLauncher.launch(suggestedSaveName)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_save))
            }

            OutlinedButton(
                enabled = !running && romLoaded,
                onClick = {
                    if (!romLoaded) {
                        statusText = noRomLoadedMessage
                        return@OutlinedButton
                    }
                    importSaveLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_save))
            }

            OutlinedButton(
                enabled = !running && romLoaded,
                onClick = {
                    if (!romLoaded) {
                        statusText = noRomLoadedMessage
                        return@OutlinedButton
                    }
                    exportSaveStateLauncher.launch(suggestedStateName)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_save_state))
            }

            OutlinedButton(
                enabled = !running && romLoaded,
                onClick = {
                    if (!romLoaded) {
                        statusText = noRomLoadedMessage
                        return@OutlinedButton
                    }
                    importSaveStateLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_save_state))
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
                                    modifier = Modifier.fillMaxWidth(),
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
