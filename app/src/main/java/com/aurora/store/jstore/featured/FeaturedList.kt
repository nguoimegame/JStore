/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.store.jstore.ui.store.StoreCatalog
import com.aurora.store.jstore.ui.store.StoreCatalog.CatalogApp
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONException

/**
 * The Jaecoo server's featured list (`<server>/api/featured.json`). [fetch] returns the body
 * of a URL, or null when it cannot be reached; it blocks, so call [load] off the main thread.
 * The last good body is kept in [cacheFile] for offline starts.
 */
class FeaturedList(private val fetch: (url: String) -> String?, private val cacheFile: File) {

    /** The featured apps, else those of the last good load, else none. */
    fun load(baseUrl: String, language: String): List<CatalogApp> {
        val body = fetchOrNull("$baseUrl/api/featured.json")
        val apps = body?.let { parseOrNull(it, language) }
        if (body != null && apps != null) {
            writeCache(body)
            return apps
        }
        return cached(language)
    }

    /** The apps of the last good load, without trying the network; none when nothing is cached. */
    fun cached(language: String): List<CatalogApp> =
        readCache()?.let { parseOrNull(it, language) } ?: emptyList()

    /** A server address the HTTP client rejects (no scheme, say) is a server that did not answer. */
    private fun fetchOrNull(url: String): String? = try {
        fetch(url)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseOrNull(json: String, language: String): List<CatalogApp>? = try {
        StoreCatalog.parse(json, language, defaultName = "")
    } catch (_: JSONException) {
        null
    }

    private fun readCache(): String? = try {
        if (cacheFile.isFile) cacheFile.readText() else null
    } catch (_: IOException) {
        null
    }

    /**
     * Writes to a temp file next to the cache, then moves it over the cache in one step, so a
     * failed or concurrent write (the Kho tab and [JaecooServerApps] share this file) never leaves
     * a partial body behind.
     */
    private fun writeCache(json: String) {
        var temp: File? = null
        try {
            val dir = cacheFile.absoluteFile.parentFile ?: return
            dir.mkdirs()
            temp = File.createTempFile(cacheFile.name, ".tmp", dir).also { it.writeText(json) }
            Files.move(
                temp.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: IOException) {
            // Without a cache the next offline start shows no featured apps; nothing else breaks.
            temp?.delete()
        }
    }
}
