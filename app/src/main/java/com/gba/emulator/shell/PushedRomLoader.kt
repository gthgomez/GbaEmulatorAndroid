package com.gba.emulator.shell

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Debug-only ROM ingest from [Context.cacheDir]/rom-smoke.gba after `adb push`.
 */
object PushedRomLoader {
    const val CACHE_ROM_NAME = "rom-smoke.gba"

    fun cacheRomFile(context: Context): File = File(context.cacheDir, CACHE_ROM_NAME)

    fun hasPushedRom(context: Context): Boolean {
        val file = cacheRomFile(context)
        return file.isFile && file.length() > 0L
    }

    fun load(context: Context): Result<RomLoader.LoadedRom> {
        val file = cacheRomFile(context)
        if (!file.isFile) {
            return Result.failure(IOException("No pushed ROM at ${file.absolutePath}"))
        }
        return try {
            val bytes = file.readBytes()
            RomValidator.validate(bytes).map { validation ->
                RomLoader.LoadedRom(
                    bytes = bytes,
                    displayName = file.name,
                    title = RomLoader.readCartridgeTitle(bytes),
                    sizeBytes = bytes.size,
                    validation = validation,
                )
            }
        } catch (ex: IOException) {
            Result.failure(ex)
        } catch (ex: IllegalArgumentException) {
            Result.failure(IOException(ex.message ?: "Invalid ROM", ex))
        }
    }
}
