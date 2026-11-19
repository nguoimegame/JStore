/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.embed

import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale

/**
 * What JaecooApp tells the store screen about the place it is shown in (docs/jstore-contract.md,
 * hostConfig). The screen follows it: language, light or dark, and short or tall.
 */
data class StoreHostConfig(
    val language: String?,
    val nightMode: Boolean?,
    val screenHeightDp: Int?,
    val serverUrls: List<String>,
    val initialSection: String
) {

    /** Same split JaecooApp uses: 1024dp and taller is the one-column layout. */
    fun isTall(): Boolean = (screenHeightDp ?: 0) >= HEIGHT_DP_TALL

    /** A change that resources cannot follow on their own: the views are inflated again. */
    fun needsReinflate(old: StoreHostConfig): Boolean =
        old.nightMode != nightMode || old.language != language || old.isTall() != isTall()

    /** [base] with the host's height, light/dark mode and language laid over it. */
    fun applyTo(base: Configuration): Configuration {
        val updated = Configuration(base)
        updated.screenHeightDp = screenHeightDp ?: base.screenHeightDp
        if (nightMode != null) {
            val flag = if (nightMode) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            updated.uiMode = (base.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or flag
        }
        if (!language.isNullOrBlank()) updated.setLocale(Locale.forLanguageTag(language))
        return updated
    }

    companion object {
        const val HEIGHT_DP_TALL = 1024

        const val KEY_LANGUAGE = "language"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_SCREEN_HEIGHT_DP = "screen_height_dp"
        const val KEY_SERVER_URLS = "server_urls"
        const val KEY_INITIAL_SECTION = "initial_section"

        const val SECTION_JAECOO = "jaecoo"
        const val SECTION_PLAY = "play"
        const val SECTION_MANAGE = "manage"
        val SECTIONS = listOf(SECTION_JAECOO, SECTION_PLAY, SECTION_MANAGE)

        fun fromMap(map: Map<String, Any?>): StoreHostConfig {
            val section =
                (map[KEY_INITIAL_SECTION] as? String)?.takeIf { it in SECTIONS } ?: SECTION_JAECOO
            return StoreHostConfig(
                language = map[KEY_LANGUAGE] as? String,
                nightMode = map[KEY_NIGHT_MODE] as? Boolean,
                screenHeightDp = map[KEY_SCREEN_HEIGHT_DP] as? Int,
                serverUrls = (map[KEY_SERVER_URLS] as? List<*>)
                    .orEmpty()
                    .filterIsInstance<String>(),
                initialSection = section
            )
        }

        fun fromBundle(bundle: Bundle?): StoreHostConfig {
            if (bundle == null) return fromMap(emptyMap())
            return fromMap(
                buildMap {
                    for (key in bundle.keySet()) {
                        @Suppress("DEPRECATION")
                        put(key, bundle.get(key))
                    }
                }
            )
        }
    }
}

/** Who may use the store screen service. */
object StoreUiCallers {
    private const val JAECOO_APP_PACKAGE = "com.example.jaecooapp"

    fun isAllowedCaller(packages: Array<String>?): Boolean =
        packages?.contains(JAECOO_APP_PACKAGE) == true
}
