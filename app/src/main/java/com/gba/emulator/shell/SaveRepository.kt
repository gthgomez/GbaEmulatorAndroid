package com.gba.emulator.shell

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * SAF read/write helpers for cartridge saves and save-state blobs.
 * Writes use backup-before-overwrite per controlled-beta rollback guidance.
 */
object SaveRepository {
    fun readAllBytes(context: Context, uri: Uri): Result<ByteArray> {
        val resolver = context.contentResolver
        return try {
            resolver.openInputStream(uri)?.use { input ->
                Result.success(input.readBytes())
            } ?: Result.failure(IOException("Could not open $uri"))
        } catch (ex: IOException) {
            Result.failure(ex)
        } catch (ex: SecurityException) {
            Result.failure(IOException("Permission denied reading $uri", ex))
        }
    }

    /**
     * Writes [data] to [uri]. If the target already has content, a copy is stored under
     * [Context.cacheDir] before overwrite (best-effort; does not delete the primary file on failure).
     */
    fun writeBytesWithBackup(context: Context, uri: Uri, data: ByteArray): Result<File?> {
        val resolver = context.contentResolver
        return try {
            val backupFile = readAllBytes(context, uri).getOrNull()?.let { existing ->
                if (existing.isEmpty()) {
                    null
                } else {
                    val displayName = queryDisplayName(resolver, uri) ?: "save.bin"
                    val backup = File(
                        context.cacheDir,
                        backupFileName(displayName),
                    )
                    backup.writeBytes(existing)
                    backup
                }
            }
            resolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(data)
            } ?: return Result.failure(IOException("Could not open $uri for writing"))
            Result.success(backupFile)
        } catch (ex: IOException) {
            Result.failure(ex)
        } catch (ex: SecurityException) {
            Result.failure(IOException("Permission denied writing $uri", ex))
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun backupFileName(originalName: String): String {
        val safe = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dot = safe.lastIndexOf('.')
        return if (dot > 0) {
            safe.substring(0, dot) + ".bak" + safe.substring(dot)
        } else {
            "$safe.bak"
        }
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

    private fun InputStream.readBytes(): ByteArray {
        val buffer = mutableListOf<Byte>()
        val chunk = ByteArray(8192)
        while (true) {
            val read = read(chunk)
            if (read <= 0) {
                break
            }
            for (i in 0 until read) {
                buffer.add(chunk[i])
            }
        }
        return buffer.toByteArray()
    }

    private fun OutputStream.write(data: ByteArray) {
        write(data, 0, data.size)
        flush()
    }
}
