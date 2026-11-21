/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The app catalogue on the Jaecoo server (`<server>/api/apps.json`), as JaecooApp's store
 * used to read it. [fetch] returns the body of a URL, or null when it cannot be reached;
 * it blocks, so call [load] off the main thread.
 */
class StoreCatalog(
    private val fetch: (url: String) -> String?,
    private val cache: CatalogCache
) {

    /** The catalogue and the server that answered: relative paths are resolved against it. */
    data class Catalog(val baseUrl: String, val apps: List<CatalogApp>, val fromCache: Boolean)

    data class CatalogApp(
        val id: String,
        val name: String,
        val description: String,
        val iconUrl: String,
        val category: String,
        val version: String,
        val packageName: String,
        val downloadUrl: String,
        val fileSize: String,
        /** Hex SHA-256 of the file at [downloadUrl]; empty when the catalogue gives none. */
        val sha256: String
    )

    /** The body and server of the last catalogue that loaded. */
    interface CatalogCache {
        fun read(): Pair<String, String>?
        fun write(baseUrl: String, json: String)
    }

    /**
     * Tries each server in order and keeps the first catalogue that parses; when none does,
     * the last good one from [cache]; null when there is not one either.
     */
    fun load(serverUrls: List<String>, language: String): Catalog? {
        for (baseUrl in serverUrls) {
            val body = fetchOrNull("$baseUrl/api/apps.json") ?: continue
            val apps = parseOrNull(body, language) ?: continue
            cache.write(baseUrl, body)
            return Catalog(baseUrl, apps, fromCache = false)
        }
        val (baseUrl, body) = cache.read() ?: return null
        return parseOrNull(body, language)?.let { Catalog(baseUrl, it, fromCache = true) }
    }

    /** A server address the HTTP client rejects (no scheme, say) is a server that did not answer. */
    private fun fetchOrNull(url: String): String? = try {
        fetch(url)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseOrNull(json: String, language: String): List<CatalogApp>? = try {
        parse(json, language)
    } catch (_: JSONException) {
        null
    }

    /** Keeps the catalogue on disk, in [directory]. */
    class FileCatalogCache(private val directory: File) : CatalogCache {
        private val body get() = File(directory, "apps.json")
        private val server get() = File(directory, "server.txt")

        override fun read(): Pair<String, String>? = try {
            if (body.isFile && server.isFile) server.readText() to body.readText() else null
        } catch (_: IOException) {
            null
        }

        override fun write(baseUrl: String, json: String) {
            try {
                directory.mkdirs()
                body.writeText(json)
                server.writeText(baseUrl)
            } catch (_: IOException) {
                // Without a cache the next offline start shows an empty catalogue; nothing else breaks.
            }
        }
    }

    companion object {

        /** A full link is kept; a relative one is appended to [baseUrl]. */
        fun resolveUrl(baseUrl: String, path: String): String =
            if (path.startsWith("http")) path else "$baseUrl$path"

        /** True when [expected] is empty (the catalogue gave none) or matches the file's SHA-256. */
        fun sha256Matches(file: File, expected: String): Boolean {
            if (expected.isEmpty()) return true
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") {
                "%02x".format(it)
            }.equals(expected.trim(), ignoreCase = true)
        }

        private const val BUFFER_BYTES = 64 * 1024

        /**
         * Description in [language], else English, else Vietnamese, else the plain
         * `description` field. Java reports Indonesian as "in", the catalogue says "id".
         */
        fun resolveDescription(obj: JSONObject, language: String): String {
            val raw = language.lowercase().trim()
            val lang = if (raw == "in") "id" else raw
            val descriptions = obj.optJSONObject("descriptions")
            val order = listOfNotNull(lang, "en".takeIf { lang != "en" }, "vi")
            return order.firstNotNullOfOrNull { key ->
                descriptions?.optString(key, "")?.trim()?.ifEmpty { null }
            } ?: obj.optString("description", "")
        }

        fun parse(
            json: String,
            language: String,
            defaultName: String = "Unknown App"
        ): List<CatalogApp> {
            val array = JSONArray(json)
            return (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                CatalogApp(
                    id = obj.optString("id", index.toString()),
                    name = obj.optString("name", defaultName),
                    description = resolveDescription(obj, language),
                    iconUrl = obj.optString("iconUrl", ""),
                    category = obj.optString("category", ""),
                    version = obj.optString("version", ""),
                    packageName = obj.optString("packageName", ""),
                    downloadUrl = obj.optString("downloadUrl", ""),
                    fileSize = obj.optString("fileSize", ""),
                    sha256 = obj.optString("sha256", "")
                )
            }
        }
    }
}
