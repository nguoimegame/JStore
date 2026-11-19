/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

/** The cases JaecooApp's own store tests covered before the screen moved here. */
class StoreCatalogParseTest {

    private val multilingual = JSONObject(
        """{"id":"litube","name":"Litube","description":"Bản YouTube gọn nhẹ",
        "descriptions":{"vi":"Bản YouTube gọn nhẹ tiếng Việt","en":"Lightweight YouTube client in English",
        "ru":"Легковесный клиент YouTube на русском","zh":"轻量级 YouTube 客户端中文"}}"""
    )

    @Test
    fun descriptionFollowsEachLanguage() {
        assertThat(
            StoreCatalog.resolveDescription(multilingual, "vi")
        ).isEqualTo("Bản YouTube gọn nhẹ tiếng Việt")
        assertThat(
            StoreCatalog.resolveDescription(multilingual, "ru")
        ).isEqualTo("Легковесный клиент YouTube на русском")
        assertThat(
            StoreCatalog.resolveDescription(multilingual, "zh")
        ).isEqualTo("轻量级 YouTube 客户端中文")
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertThat(StoreCatalog.resolveDescription(multilingual, "es_unsupported"))
            .isEqualTo("Lightweight YouTube client in English")
    }

    @Test
    fun legacyIndonesianCodeMapsToId() {
        val json =
            JSONObject(
                """{"description":"root","descriptions":{"vi":"vi","en":"en","id":"Bahasa"}}"""
            )
        assertThat(StoreCatalog.resolveDescription(json, "in")).isEqualTo("Bahasa")
    }

    @Test
    fun noDescriptionsObjectFallsBackToRootDescription() {
        val json =
            JSONObject("""{"id":"old","name":"Old","description":"Legacy fallback description"}""")
        assertThat(
            StoreCatalog.resolveDescription(json, "en")
        ).isEqualTo("Legacy fallback description")
        assertThat(
            StoreCatalog.resolveDescription(json, "vi")
        ).isEqualTo("Legacy fallback description")
    }

    @Test
    fun parseUsesPreferredLanguage() {
        val array = """[{"id":"litube","name":"Litube","description":"Default desc",
            "descriptions":{"vi":"Tiếng Việt","en":"English"}}]"""
        assertThat(StoreCatalog.parse(array, "en").single().description).isEqualTo("English")
        assertThat(StoreCatalog.parse(array, "vi").single().description).isEqualTo("Tiếng Việt")
    }

    @Test
    fun estimatedBytesFromCatalogueSizes() {
        assertThat(StoreOps.parseEstimatedBytes("103 MB")).isEqualTo(103 * 1024 * 1024L)
        assertThat(
            StoreOps.parseEstimatedBytes("22.8 MB")
        ).isEqualTo((22.8 * 1024 * 1024L).toLong())
        assertThat(StoreOps.parseEstimatedBytes("1 GB")).isEqualTo(1024 * 1024 * 1024L)
        assertThat(StoreOps.parseEstimatedBytes("")).isEqualTo(0L)
    }
}
