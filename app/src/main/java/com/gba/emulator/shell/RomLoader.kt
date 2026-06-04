package com.gba.emulator.shell

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

object RomLoader {
    const val MAX_ROM_BYTES = 32 * 1024 * 1024
    private const val HEADER_TITLE_OFFSET = 0xA0
    private const val HEADER_TITLE_LENGTH = 12

    data class LoadedRom(
        val bytes: ByteArray,
        val displayName: String,
        val title: String,
        val sizeBytes: Int,
        val validation: RomValidator.ValidationResult,
    )

    fun load(context: Context, uri: Uri): Result<LoadedRom> {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: uri.lastPathSegment ?: "rom.gba"
        return try {
            val declaredSize = querySize(resolver, uri)
            if (declaredSize != null) {
                if (declaredSize == 0L) {
                    return Result.failure(IOException(RomValidator.ValidationError.Empty.message))
                }
                if (declaredSize > MAX_ROM_BYTES) {
                    return Result.failure(
                        IOException(
                            RomValidator.ValidationError.TooLarge(MAX_ROM_BYTES / (1024 * 1024)).message,
                        ),
                    )
                }
                if ((declaredSize and 1L) != 0L) {
                    return Result.failure(IOException(RomValidator.ValidationError.OddSize.message))
                }
            }
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytesUpTo(MAX_ROM_BYTES)
                if (bytes.isEmpty()) {
                    Result.failure(IOException(RomValidator.ValidationError.Empty.message))
                } else {
                    RomValidator.validate(bytes).map { validation ->
                        LoadedRom(
                            bytes = bytes,
                            displayName = displayName,
                            title = readCartridgeTitle(bytes),
                            sizeBytes = bytes.size,
                            validation = validation,
                        )
                    }
                }
            } ?: Result.failure(IOException("Could not open ROM"))
        } catch (ex: IOException) {
            Result.failure(ex)
        } catch (ex: SecurityException) {
            Result.failure(IOException("Permission denied reading ROM", ex))
        } catch (ex: IllegalArgumentException) {
            Result.failure(IOException(ex.message ?: "Invalid ROM", ex))
        }
    }

    fun readCartridgeTitle(rom: ByteArray): String {
        if (rom.size < HEADER_TITLE_OFFSET + HEADER_TITLE_LENGTH) {
            return "Unknown"
        }
        val raw = rom.copyOfRange(HEADER_TITLE_OFFSET, HEADER_TITLE_OFFSET + HEADER_TITLE_LENGTH)
        return raw.decodeToString()
            .trim('\u0000', ' ')
            .ifEmpty { "Unknown" }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                return cursor.getLong(index)
            }
        }
        return null
    }

    private fun InputStream.readBytesUpTo(maxBytes: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val toRead = min(chunk.size, maxBytes + 1 - total)
            if (toRead <= 0) {
                break
            }
            val read = read(chunk, 0, toRead)
            if (read <= 0) {
                break
            }
            total += read
            if (total > maxBytes) {
                throw IOException(
                    RomValidator.ValidationError.TooLarge(maxBytes / (1024 * 1024)).message,
                )
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
