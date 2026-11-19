/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import org.json.JSONException
import org.json.JSONObject

/** One published jstore build: [versionName] without the tag's `v`, and where to fetch it. */
data class JstoreRelease(val versionName: String, val apkUrl: String, val sha256: String)

/**
 * The latest jstore release on GitHub (`nguoimegame/JStore`). The asset names are part of
 * docs/jstore-contract.md. `fetch` returns the body of a URL or null when it cannot be read.
 */
object JstoreReleases {

    const val LATEST_URL = "https://api.github.com/repos/nguoimegame/JStore/releases/latest"
    private const val APK_ASSET = "jstore.apk"
    private const val SHA256_ASSET = "jstore.apk.sha256"
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    /** What [parse] reads from GitHub's release JSON, before the checksum is fetched. */
    data class Parsed(val versionName: String, val apkUrl: String, val sha256Url: String)

    /** Null when GitHub cannot be reached or the release is incomplete or its checksum unusable. */
    fun latest(fetch: (url: String) -> String?): JstoreRelease? {
        val parsed = fetch(LATEST_URL)?.let(::parse) ?: return null
        val sha256 = fetch(parsed.sha256Url)?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
        return if (sha256.matches(
                SHA256_HEX
            )
        ) {
            JstoreRelease(parsed.versionName, parsed.apkUrl, sha256)
        } else {
            null
        }
    }

    /** Null when the release lacks a vX.Y.Z-style tag or either asset, or is not JSON. */
    fun parse(json: String): Parsed? {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val versionName = root.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        val assets = root.optJSONArray("assets")
        val urls = mutableMapOf<String, String>()
        for (i in 0 until (assets?.length() ?: 0)) {
            val asset = assets?.optJSONObject(i) ?: continue
            urls[asset.optString("name")] = asset.optString("browser_download_url")
        }
        val apkUrl = urls[APK_ASSET].orEmpty()
        val sha256Url = urls[SHA256_ASSET].orEmpty()
        val complete = versionName.isNotEmpty() &&
            apkUrl.startsWith("https://") &&
            sha256Url.startsWith("https://")
        return if (complete) Parsed(versionName, apkUrl, sha256Url) else null
    }
}
