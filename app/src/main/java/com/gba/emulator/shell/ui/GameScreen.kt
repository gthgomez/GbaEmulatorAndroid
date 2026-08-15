package com.gba.emulator.shell.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gba.emulator.shell.ApuAudioEngine
import com.gba.emulator.shell.AudioPlaybackMode
import com.gba.emulator.shell.BuildConfig
import com.gba.emulator.shell.EmulationFramePacer
import com.gba.emulator.shell.EmulatorSession
import com.gba.emulator.shell.GbaRuntimeBridge
import com.gba.emulator.shell.PlaybackSpeedController
import com.gba.emulator.shell.PlaytestDataLogger
import com.gba.emulator.shell.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "FlowframeEmu"
private const val WARMUP_STREAK_FOR_MESSAGE = 3
private const val DEBUG_OVERLAY_AUTO_HIDE_MS = 3_000L
private const val AUDIT_FRAME = 60
private const val DIAGNOSTICS_SAMPLE_INTERVAL = 30
private const val PREFS_NAME = "gameplay"
private const val PREFS_SPEED = "playback_speed"
private const val NON_ZERO_PIXEL_THRESHOLD = 1_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    session: EmulatorSession,
    romTitle: String,
    onExit: () -> Unit,
) {
    var hasPresentedFrame by remember { mutableStateOf(false) }
    var forcedBlankHint by remember { mutableStateOf(false) }
    var zeroScanlineStreak by remember { mutableIntStateOf(0) }
    var showWarmupMessage by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf("") }
    var debugOverlay by remember { mutableStateOf("") }
    var showDebugOverlay by remember { mutableStateOf(BuildConfig.DEBUG) }
    var running by remember { mutableStateOf(true) }
    var pausedByLifecycle by remember { mutableStateOf(false) }
    var ignoreAbnormalStop by remember { mutableStateOf(false) }
    var restartGeneration by remember { mutableIntStateOf(0) }
    var buttonMask by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var auditLogged by remember { mutableStateOf(false) }
    var audioMode by remember { mutableStateOf(AudioPlaybackMode.Sync) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var playbackSpeed by remember { mutableStateOf(loadPersistedSpeed(prefs)) }
    val speedController = remember { PlaybackSpeedController(playbackSpeed) }
    val framePacer = remember { EmulationFramePacer() }
    val currentAudioMode by rememberUpdatedState(audioMode)
    val viewportController = remember { GameViewportSurfaceController() }
    val audioEngine = remember { ApuAudioEngine() }
    val logger = remember { PlaytestDataLogger(context) }
    val scope = rememberCoroutineScope()

    val runtimeErrorTemplate = stringResource(R.string.game_runtime_error)
    val stopReasonTemplate = stringResource(R.string.game_stop_reason)
    val debugOverlayTemplate = stringResource(R.string.game_debug_overlay)
    val loadingGraphicsText = stringResource(R.string.game_loading_graphics)
    val warmingUpText = stringResource(R.string.game_warming_up)
    val steadyMusicHelper = stringResource(R.string.game_steady_music_helper)
    val activity = context.findActivity()
    val window = activity?.window
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, session, audioEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    pausedByLifecycle = true
                    audioEngine.clear()
                    session.notifyEmulationPaused()
                }
                Lifecycle.Event.ON_START -> {
                    pausedByLifecycle = false
                    audioEngine.preRollSilence()
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
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    LaunchedEffect(playbackSpeed) {
        speedController.setSpeed(playbackSpeed)
    }

    LaunchedEffect(audioMode, playbackSpeed) {
        when (audioMode) {
            AudioPlaybackMode.Sync -> {
                audioEngine.setSteadyMusicEnabled(false)
                val rate = if (playbackSpeed == PlaybackSpeedController.PlaybackSpeed.One) {
                    1f
                } else {
                    speedController.playbackRateMultiplier
                }
                audioEngine.setPlaybackRateMultiplier(rate)
            }
            AudioPlaybackMode.SteadyMusic -> {
                audioEngine.setSteadyMusicEnabled(true)
                audioEngine.setPlaybackRateMultiplier(1f)
            }
            AudioPlaybackMode.Mute -> {
                audioEngine.setSteadyMusicEnabled(false)
                audioEngine.setPlaybackRateMultiplier(1f)
            }
        }
    }

    LaunchedEffect(debugOverlay) {
        if (!BuildConfig.DEBUG || debugOverlay.isEmpty()) {
            return@LaunchedEffect
        }
        showDebugOverlay = true
        delay(DEBUG_OVERLAY_AUTO_HIDE_MS)
        showDebugOverlay = false
    }

    BackHandler(onBack = onExit)

    LaunchedEffect(buttonMask, hasPresentedFrame) {
        if (!isActive || !session.isActive || !hasPresentedFrame) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            session.setButtonMask(buttonMask)
        }
    }

    LaunchedEffect(session, running, pausedByLifecycle, ignoreAbnormalStop, restartGeneration) {
        framePacer.reset()
        auditLogged = false
        val recycledPixels = ShortArray(GbaRuntimeBridge.FRAMEBUFFER_PIXELS)
        val recycledAudio = ShortArray(1098)
        try {
            logger.startSession(romTitle)
            while (isActive && running && !pausedByLifecycle && session.isActive) {
                val frameStart = withFrameNanos { nanos -> nanos }
                val wake = framePacer.onWake(frameStart)
                if (wake.slotsDue == 0) {
                    continue
                }

                val stepsToRun = speedController.stepsForWallSlots(wake.slotsDue)
                if (stepsToRun == 0) {
                    continue
                }

                var lastStep: EmulatorSession.PresentedFrameStep? = null
                var lastBatchFrames = 0
                withContext(Dispatchers.Default) {
                    repeat(stepsToRun) {
                        val step = session.stepFrameAndPresent(
                            pixelsDest = recycledPixels,
                            audioDest = recycledAudio
                        ) ?: return@withContext
                        lastStep = step
                        lastBatchFrames = step.audioBatchSize / 2
                        if (currentAudioMode != AudioPlaybackMode.Mute && step.audioBatchSize > 0) {
                            audioEngine.enqueueBatch(step.audioBatch, step.audioBatchSize)
                        }
                    }
                }

                val presented = lastStep
                if (presented == null) {
                    if (session.isActive && running && !pausedByLifecycle) {
                        // Transient null step from an active session: skip this frame instead
                        // of killing the loop; the next iteration re-attempts.
                        Log.w(
                            LOG_TAG,
                            "step_frame_null_transient slotsDue=${wake.slotsDue} " +
                                "stepsToRun=$stepsToRun; skipping frame",
                        )
                        continue
                    }
                    // stepFrameAndPresent returns null only when the session is closed or its
                    // native handle is gone — a terminal condition.
                    Log.w(
                        LOG_TAG,
                        "loop_exit step_frame_null sessionActive=${session.isActive} " +
                            "running=$running",
                    )
                    break
                }
                val frame = presented.frame
                val pixels = presented.pixels

                when (frame.status) {
                    GbaRuntimeBridge.RuntimeStatus.Ok -> {
                        val videoDiagnostics = if (BuildConfig.DEBUG &&
                            frameCount % DIAGNOSTICS_SAMPLE_INTERVAL == 0
                        ) {
                            session.getVideoDiagnostics()
                        } else {
                            null
                        }
                        withContext(Dispatchers.Default) {
                            val frameBitmap = viewportController.prepareBitmap(pixels)
                            viewportController.presentOnSurface(frameBitmap)
                        }
                        hasPresentedFrame = true
                        forcedBlankHint = videoDiagnostics?.forcedBlank == true

                        val hasVisibleContent = frame.frameComplete ||
                            hasEnoughNonZeroPixels(pixels, videoDiagnostics)

                        if (hasVisibleContent || frame.renderedScanlines > 0) {
                            zeroScanlineStreak = 0
                            showWarmupMessage = false
                        } else {
                            zeroScanlineStreak += 1
                            showWarmupMessage = zeroScanlineStreak >= WARMUP_STREAK_FOR_MESSAGE
                            if (zeroScanlineStreak == WARMUP_STREAK_FOR_MESSAGE) {
                                Log.w(
                                    LOG_TAG,
                                    "no_ppu_render streak=$zeroScanlineStreak steps=${frame.executedSteps} " +
                                        "status=${frameStatusLabel(frame)} pc=0x${
                                            frame.finalPc.toUInt().toString(16)
                                        }",
                                )
                            }
                        }
                        frameCount += 1
                        if (frameCount == AUDIT_FRAME && !auditLogged) {
                            auditLogged = true
                            val allZero = pixels.all { it == 0.toShort() }
                            if (frame.renderedScanlines == 0 || allZero) {
                                Log.w(
                                    LOG_TAG,
                                    "audit_frame60 scanlines=${frame.renderedScanlines} allZero=$allZero " +
                                        "cyclesDelta=${frame.schedulerCyclesDelta}",
                                )
                            }
                            Log.i(
                                LOG_TAG,
                                "audit_audio batchFrames=$lastBatchFrames " +
                                    "playbackUnderruns=${audioEngine.playbackUnderruns} " +
                                    "coreUnderruns=${frame.audioUnderruns}",
                            )
                        }

                        val frameMs = if (lastFrameNanos > 0L) {
                            (frameStart - lastFrameNanos) / 1_000_000.0
                        } else {
                            0.0
                        }
                        lastFrameNanos = frameStart
                        val playbackUnderruns = audioEngine.playbackUnderruns

                        logger.logFrame(
                            PlaytestDataLogger.FrameMetrics(
                                frameIndex = frameCount,
                                frameMs = frameMs,
                                cyclesDelta = frame.schedulerCyclesDelta,
                                executedSteps = frame.executedSteps,
                                renderedScanlines = frame.renderedScanlines,
                                audioSamples = frame.audioSamples,
                                playbackUnderruns = playbackUnderruns,
                                coreUnderruns = frame.audioUnderruns,
                                stateHash = frame.stateHash,
                                stopReason = frame.stopReason.name,
                                finalPc = "0x${frame.finalPc.toUInt().toString(16)}"
                            )
                        )

                        if (BuildConfig.DEBUG) {
                            val behindLabel = if (wake.behindSchedule) " behind" else ""
                            val videoSuffix = videoDiagnostics?.let { diag ->
                                " · DISPCNT=0x${diag.dispcnt.toString(16)} nonZero=${diag.nonZeroPixelCount} " +
                                    "sample=0x${diag.sampleRgb565.toString(16)}"
                            }.orEmpty()
                            debugOverlay = String.format(
                                debugOverlayTemplate,
                                frameCount,
                                frameMs,
                                frameStatusLabel(frame),
                                frame.renderedScanlines,
                                frame.executedSteps,
                                frame.finalPc.toUInt().toString(16),
                                playbackUnderruns,
                                frame.schedulerCyclesDelta,
                                playbackSpeed.label,
                                lastBatchFrames,
                                frame.audioUnderruns,
                                behindLabel,
                            ) + videoSuffix
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
                                } steps=${frame.executedSteps} scanlines=${frame.renderedScanlines}",
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
            logger.stopSession()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowframeColors.PlayStageBlack),
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = romTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = FlowframeColors.ViewportOverlay,
                    )
                    if (playbackSpeed != PlaybackSpeedController.PlaybackSpeed.One) {
                        Text(
                            text = playbackSpeed.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = FlowframeColors.AccentSpeed,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.exit_game),
                        tint = FlowframeColors.ViewportOverlay,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                session.reloadLastRom()
                            }
                            overlayText = ""
                            ignoreAbnormalStop = false
                            frameCount = 0
                            lastFrameNanos = 0L
                            hasPresentedFrame = false
                            forcedBlankHint = false
                            zeroScanlineStreak = 0
                            showWarmupMessage = false
                            auditLogged = false
                            framePacer.reset()
                            // Restart the Oboe stream so the restarted session starts from a
                            // clean ring buffer and reset underrun counters (clear() alone does
                            // not tear the stream down, which is what made playbackUnderruns
                            // explode after restart).
                            audioEngine.stop()
                            audioEngine.start()
                            running = true
                            restartGeneration += 1
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.restart_game),
                        tint = FlowframeColors.ViewportOverlay,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = FlowframeColors.PlayStageBlack,
                titleContentColor = FlowframeColors.ViewportOverlay,
                navigationIconContentColor = FlowframeColors.ViewportOverlay,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FlowframeColors.ChromeBar)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.game_steady_music),
                style = MaterialTheme.typography.bodySmall,
                color = FlowframeColors.ChromeText,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = audioMode == AudioPlaybackMode.SteadyMusic,
                onCheckedChange = { enabled ->
                    audioMode = if (enabled) {
                        AudioPlaybackMode.SteadyMusic
                    } else {
                        AudioPlaybackMode.Sync
                    }
                },
            )
        }
        SpeedSelectorRow(
            selected = playbackSpeed,
            onSelect = { speed ->
                playbackSpeed = speed
                prefs.edit().putString(PREFS_SPEED, speed.name).apply()
            },
        )

        if (audioMode == AudioPlaybackMode.SteadyMusic &&
            playbackSpeed != PlaybackSpeedController.PlaybackSpeed.One
        ) {
            Text(
                text = steadyMusicHelper,
                style = MaterialTheme.typography.bodySmall,
                color = FlowframeColors.ChromeTextDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        GameViewportFrame(
            screenWidth = GbaRuntimeBridge.SCREEN_WIDTH,
            screenHeight = GbaRuntimeBridge.SCREEN_HEIGHT,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GameViewportSurface(
                    controller = viewportController,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!hasPresentedFrame || showWarmupMessage) {
                    Text(
                        text = if (!hasPresentedFrame) {
                            loadingGraphicsText
                        } else if (forcedBlankHint && BuildConfig.DEBUG) {
                            stringResource(R.string.game_debug_forced_blank)
                        } else {
                            warmingUpText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlowframeColors.ChromeTextDim,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Frosted pause overlay
                if (pausedByLifecycle) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.paused_overlay),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && showDebugOverlay && debugOverlay.isNotEmpty()) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = debugOverlay,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlowframeColors.ChromeTextDim,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
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
                    color = FlowframeColors.ChromeText,
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
            enabled = hasPresentedFrame,
            onMaskChanged = { buttonMask = it },
            modifier = Modifier.background(FlowframeColors.PlayStageBlack),
        )
    }
}

private fun loadPersistedSpeed(
    prefs: android.content.SharedPreferences,
): PlaybackSpeedController.PlaybackSpeed {
    val stored = prefs.getString(PREFS_SPEED, PlaybackSpeedController.PlaybackSpeed.One.name)
    return PlaybackSpeedController.PlaybackSpeed.entries.firstOrNull { it.name == stored }
        ?: PlaybackSpeedController.PlaybackSpeed.One
}

private fun hasEnoughNonZeroPixels(
    pixels: ShortArray,
    diagnostics: GbaRuntimeBridge.VideoDiagnostics?,
): Boolean {
    if (diagnostics != null && diagnostics.nonZeroPixelCount > NON_ZERO_PIXEL_THRESHOLD) {
        return true
    }
    var count = 0
    for (pixel in pixels) {
        if (pixel != 0.toShort() && ++count > NON_ZERO_PIXEL_THRESHOLD) {
            return true
        }
    }
    return false
}

internal fun frameStatusLabel(frame: GbaRuntimeBridge.FrameResult): String {
    if (frame.frameComplete) {
        return "FrameComplete"
    }
    if (frame.renderedScanlines == 0 &&
        frame.executedSteps >= EmulatorSession.DEFAULT_MAX_INSTRUCTIONS_PER_FRAME
    ) {
        return "MaxSteps"
    }
    if (frame.renderedScanlines == 0) {
        return "StepBudget"
    }
    return "Partial(${frame.renderedScanlines})"
}
