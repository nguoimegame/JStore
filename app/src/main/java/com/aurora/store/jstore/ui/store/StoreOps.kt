/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.Context
import com.aurora.store.data.network.HttpClient
import com.aurora.store.jstore.JaecooConnection
import com.aurora.store.jstore.JstoreLog
import com.aurora.store.jstore.PlayUpdates
import java.io.File
import java.io.IOException

/**
 * What the store screen does with the network and with JaecooApp: download an APK, hand it
 * to JaecooApp to install, and fetch what Google Play would install. Everything blocks, so
 * call it off the main thread.
 */
class StoreOps(
    private val context: Context,
    private val httpClient: HttpClient,
    private val play: PlayUpdates
) {

    class DownloadFailedException(message: String) : Exception(message)

    /**
     * Downloads [url] to this package's lending folder; the file must match [sha256] when given.
     * [onProgress] gets 0-100. Null when the download broke, came up short or failed its check.
     */
    fun download(
        url: String,
        packageName: String,
        fileName: String,
        estimatedBytes: Long,
        sha256: String,
        onProgress: (Int) -> Unit
    ): File? {
        val dir = play.downloadDir(packageName).apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, safeFileName(fileName))
        return try {
            httpClient.call(url).use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val declared = response.body.contentLength()
                val total = if (declared > 0L) declared else estimatedBytes
                val written = response.body.byteStream().use { input ->
                    target.outputStream().use { output -> copy(input, output, total, onProgress) }
                }
                if (declared > 0L &&
                    written != declared
                ) {
                    throw IOException("Incomplete download: $written of $declared")
                }
                if (written == 0L) throw IOException("Empty download")
            }
            if (!StoreCatalog.sha256Matches(
                    target,
                    sha256
                )
            ) {
                throw IOException("Checksum mismatch for $fileName")
            }
            target
        } catch (e: IOException) {
            JstoreLog.w(TAG, "Download failed: $url", e)
            play.release(packageName)
            null
        }
    }

    /** Lends [apk] to JaecooApp, has it installed, and cleans up. */
    fun install(packageName: String, apk: File): JaecooConnection.Result = try {
        JaecooConnection.install(context, packageName, listOf(play.share(apk)))
    } finally {
        play.release(packageName)
    }

    /** Downloads the latest release of [packageName] from Google Play and has JaecooApp install it. */
    fun installFromPlay(
        packageName: String,
        onProgress: (Int) -> Unit,
        onInstalling: () -> Unit
    ): JaecooConnection.Result {
        if (!play.begin(packageName)) throw DownloadFailedException("busy")
        return try {
            val uris = play.fetch(packageName, onProgress).map { play.share(it) }
            onInstalling()
            JaecooConnection.install(context, packageName, uris)
        } catch (e: PlayUpdates.UnsupportedAppException) {
            throw DownloadFailedException(e.message.orEmpty())
        } catch (e: IOException) {
            throw DownloadFailedException(play.errorMessage(packageName, e))
        } finally {
            play.release(packageName)
        }
    }

    private fun copy(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var written = 0L
        var lastPercent = -1
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            output.write(buffer, 0, count)
            written += count
            if (totalBytes > 0L) {
                val percent = ((written * PERCENT) / totalBytes).toInt().coerceIn(0, PERCENT)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
        return written
    }

    companion object {
        private const val TAG = "StoreOps"
        private const val BUFFER_BYTES = 64 * 1024
        private const val PERCENT = 100

        /** File name from a server-chosen URL with every path character removed. */
        fun safeFileName(name: String): String {
            val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            return if (cleaned.isEmpty() || cleaned.all { it == '.' }) "download.apk" else cleaned
        }

        /** Name taken from the download path, without the query; [fallbackId].apk when there is none. */
        fun fileNameFor(downloadUrl: String, fallbackId: String): String =
            if (downloadUrl.contains("/")) {
                downloadUrl.substringAfterLast("/").substringBefore("?")
            } else {
                "$fallbackId.apk"
            }

        /** "12 MB" style sizes from the catalogue as bytes, for progress when the server sends no length. */
        fun parseEstimatedBytes(size: String): Long {
            val clean = size.trim().uppercase()
            val number = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
            val unit = when {
                clean.contains("GB") -> GB
                clean.contains("KB") -> KB
                else -> MB
            }
            return (number * unit).toLong()
        }

        private const val KB = 1024L
        private const val MB = KB * KB
        private const val GB = MB * KB
    }
}
