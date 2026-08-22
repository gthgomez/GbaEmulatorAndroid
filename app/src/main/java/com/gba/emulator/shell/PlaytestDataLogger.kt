package com.gba.emulator.shell

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Automatically logs frame-by-frame playtest data to a CSV file in [Context.getCacheDir].
 * Writes are offloaded to a background thread to prevent introducing UI render stutter.
 */
class PlaytestDataLogger(private val context: Context) {
    data class FrameMetrics(
        val frameIndex: Int,
        val frameMs: Double,
        val cyclesDelta: Long,
        val executedSteps: Int,
        val renderedScanlines: Int,
        val audioSamples: Int,
        val playbackUnderruns: Int,
        val coreUnderruns: Int,
        val stateHash: Long,
        val stopReason: String,
        val finalPc: String
    )

    private val queue = ConcurrentLinkedQueue<FrameMetrics>()
    private val executor = Executors.newSingleThreadExecutor()
    private var sessionFile: File? = null
    private var sessionActive = false

    fun startSession(romTitle: String) {
        sessionActive = Settings.Global.getInt(
            context.contentResolver,
            SETTINGS_KEY_PLAYTEST_LOGGING,
            0,
        ) != 0
        if (!sessionActive) {
            return
        }
        executor.execute {
            val safeTitle = romTitle.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val timestamp = System.currentTimeMillis()
            val fileName = "playtest_${safeTitle}_${timestamp}.csv"
            val file = File(context.cacheDir, fileName)
            sessionFile = file
            try {
                FileWriter(file).use { writer ->
                    writer.write("frame_index,frame_ms,cycles_delta,executed_steps,rendered_scanlines,audio_samples,playback_underruns,core_underruns,state_hash,stop_reason,final_pc\n")
                }
                Log.i("PlaytestDataLogger", "Started logging playtest metrics to ${file.absolutePath}")
            } catch (e: IOException) {
                Log.e("PlaytestDataLogger", "Failed to initialize CSV log file", e)
            }
        }
    }

    fun logFrame(metrics: FrameMetrics) {
        if (!sessionActive) {
            return
        }
        queue.add(metrics)
        if (queue.size >= FLUSH_THRESHOLD) {
            flush()
        }
    }

    fun flush() {
        val file = sessionFile ?: return
        val batch = mutableListOf<FrameMetrics>()
        while (true) {
            val item = queue.poll() ?: break
            batch.add(item)
        }
        if (batch.isEmpty()) return

        executor.execute {
            try {
                FileWriter(file, true).use { writer ->
                    for (m in batch) {
                        writer.write(
                            "${m.frameIndex}," +
                            "${"%.3f".format(m.frameMs)}," +
                            "${m.cyclesDelta}," +
                            "${m.executedSteps}," +
                            "${m.renderedScanlines}," +
                            "${m.audioSamples}," +
                            "${m.playbackUnderruns}," +
                            "${m.coreUnderruns}," +
                            "0x${m.stateHash.toULong().toString(16)}," +
                            "${m.stopReason}," +
                            "${m.finalPc}\n"
                        )
                    }
                }
            } catch (e: IOException) {
                Log.e("PlaytestDataLogger", "Failed to write batch to CSV log file", e)
            }
        }
    }

    fun stopSession() {
        if (!sessionActive) {
            return
        }
        sessionActive = false
        flush()
        executor.execute {
            Log.i("PlaytestDataLogger", "Stopped playtest logging. File saved: ${sessionFile?.absolutePath}")
            sessionFile = null
        }
    }

    companion object {
        private const val FLUSH_THRESHOLD = 300 // Flush batch every ~5 seconds of 60fps play

        /**
         * Runtime gate read at session start: `adb shell settings put global
         * gba_playtest_logging 1` enables per-frame CSV logging (off by default).
         */
        private const val SETTINGS_KEY_PLAYTEST_LOGGING = "gba_playtest_logging"
    }
}
