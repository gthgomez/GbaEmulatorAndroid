package com.gba.emulator.shell.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gba.emulator.shell.ApuAudioEngine
import com.gba.emulator.shell.BuildConfig
import com.gba.emulator.shell.EmulatorSession
import com.gba.emulator.shell.GbaRuntimeBridge
import com.gba.emulator.shell.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "FlowframeEmu"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    session: EmulatorSession,
    romTitle: String,
    onExit: () -> Unit,
) {
    var framebuffer by remember { mutableStateOf<ImageBitmap?>(null) }
    var overlayText by remember { mutableStateOf("") }
    var debugOverlay by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(true) }
    var pausedByLifecycle by remember { mutableStateOf(false) }
    var ignoreAbnormalStop by remember { mutableStateOf(false) }
    var restartGeneration by remember { mutableIntStateOf(0) }
    var buttonMask by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    val framebufferPresenter = remember { FramebufferPresenter() }
    val audioEngine = remember { ApuAudioEngine() }
    val scope = rememberCoroutineScope()

    val runtimeErrorTemplate = stringResource(R.string.game_runtime_error)
    val stopReasonTemplate = stringResource(R.string.game_stop_reason)
    val debugOverlayTemplate = stringResource(R.string.game_debug_overlay)
    val activity = LocalContext.current.findActivity()
    val window = activity?.window
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    pausedByLifecycle = true
                    session.notifyEmulationPaused()
                }
                Lifecycle.Event.ON_START -> {
                    pausedByLifecycle = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SideEffect {
        if (window == null) {
            return@SideEffect
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    DisposableEffect(window) {
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    DisposableEffect(session) {
        audioEngine.start()
        onDispose {
            audioEngine.stop()
        }
    }

    BackHandler(onBack = onExit)

    LaunchedEffect(buttonMask) {
        if (!isActive || !session.isActive) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            session.setButtonMask(buttonMask)
        }
    }

    LaunchedEffect(session, running, pausedByLifecycle, ignoreAbnormalStop, restartGeneration) {
        try {
            while (isActive && running && !pausedByLifecycle && session.isActive) {
                val frameStart = withFrameNanos { nanos -> nanos }
                val stepped = withContext(Dispatchers.Default) {
                    session.stepFrameAndCopy()
                } ?: break
                val (frame, pixels) = stepped
                withContext(Dispatchers.Default) {
                    session.drainAudioBatch()?.let { batch ->
                        audioEngine.queueBatch(batch)
                    }
                }
                when (frame.status) {
                    GbaRuntimeBridge.RuntimeStatus.Ok -> {
                        framebuffer = withContext(Dispatchers.Default) {
                            framebufferPresenter.update(pixels)
                        }
                        frameCount += 1
                        val frameMs = if (lastFrameNanos > 0L) {
                            (frameStart - lastFrameNanos) / 1_000_000.0
                        } else {
                            0.0
                        }
                        lastFrameNanos = frameStart
                        val playbackUnderruns = audioEngine.playbackUnderruns
                        if (BuildConfig.DEBUG) {
                            debugOverlay = String.format(
                                debugOverlayTemplate,
                                frameCount,
                                frameMs,
                                frame.stopReason.name,
                                frame.finalPc.toUInt().toString(16),
                                playbackUnderruns,
                            )
                        }
                        if (playbackUnderruns > 0 && overlayText.isEmpty()) {
                            overlayText = "Audio playback underruns: $playbackUnderruns"
                        }
                        if (frame.stopReason != GbaRuntimeBridge.StopReason.MaxSteps && !ignoreAbnormalStop) {
                            overlayText = String.format(
                                stopReasonTemplate,
                                frame.stopReason.name,
                                frame.executedSteps,
                                frame.unsupportedSteps,
                                frame.finalPc.toUInt().toString(16),
                            )
                            Log.w(
                                LOG_TAG,
                                "abnormal_stop reason=${frame.stopReason.name} pc=0x${
                                    frame.finalPc.toUInt().toString(16)
                                } steps=${frame.executedSteps}",
                            )
                            running = false
                        }
                    }
                    else -> {
                        overlayText = String.format(runtimeErrorTemplate, frame.status.name)
                        running = false
                    }
                }
            }
        } finally {
            session.setButtonMask(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowframeColors.PlayStageBlack),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = romTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE8EEF3),
                )
            },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = stringResource(R.string.exit_game),
                        tint = Color(0xFFE8EEF3),
                    )
                }
            },
            actions = {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                session.reloadLastRom()
                            }
                            overlayText = ""
                            ignoreAbnormalStop = false
                            frameCount = 0
                            lastFrameNanos = 0L
                            running = true
                            restartGeneration += 1
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.restart_game))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = FlowframeColors.PlayStageBlack,
                titleContentColor = Color(0xFFE8EEF3),
                navigationIconContentColor = Color(0xFFE8EEF3),
            ),
        )

        GameViewportFrame(
            screenWidth = GbaRuntimeBridge.SCREEN_WIDTH,
            screenHeight = GbaRuntimeBridge.SCREEN_HEIGHT,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            framebuffer?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.framebuffer_content_description),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            }
        }

        if (pausedByLifecycle) {
            Text(
                text = stringResource(R.string.paused_overlay),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8C4D0),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A2330))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (BuildConfig.DEBUG && debugOverlay.isNotEmpty()) {
            Text(
                text = debugOverlay,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8FA3B8),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (overlayText.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlowframeColors.PlayStageBlack)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = overlayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB8C4D0),
                )
                if (BuildConfig.DEBUG && !running) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(
                            onClick = {
                                ignoreAbnormalStop = true
                                overlayText = ""
                                running = true
                                restartGeneration += 1
                            },
                        ) {
                            Text(stringResource(R.string.continue_anyway))
                        }
                    }
                }
            }
        }

        TouchGameControls(
            onMaskChanged = { buttonMask = it },
            modifier = Modifier.background(FlowframeColors.PlayStageBlack),
        )
    }
}
