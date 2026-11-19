/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoreCatalogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = """[{"id":"waze","name":"Waze","packageName":"com.waze","version":"5.0",
        "downloadUrl":"/apk/waze.apk","sha256":"ab","descriptions":{"vi":"Dẫn đường","en":"Navigation"}}]"""

    private class MemCache(var saved: Pair<String, String>? = null) : StoreCatalog.CatalogCache {
        override fun read() = saved
        override fun write(baseUrl: String, json: String) {
            saved = baseUrl to json
        }
    }

    @Test
    fun triesUrlsInOrder() {
        val tried = mutableListOf<String>()
        val catalog = StoreCatalog(
            fetch = {
                tried += it
                if (it.startsWith("https://b")) json else null
            },
            cache = MemCache()
        )
        val result = catalog.load(listOf("https://a", "https://b"), "vi")!!
        assertThat(
            tried
        ).containsExactly("https://a/api/apps.json", "https://b/api/apps.json").inOrder()
        assertThat(result.baseUrl).isEqualTo("https://b")
        assertThat(result.fromCache).isFalse()
    }

    @Test
    fun successfulLoadIsCached() {
        val cache = MemCache()
        StoreCatalog({ json }, cache).load(listOf("https://a"), "vi")
        assertThat(cache.saved?.first).isEqualTo("https://a")
    }

    @Test
    fun allUrlsFailFallsBackToCache() {
        val result = StoreCatalog({
            null
        }, MemCache("https://a" to json)).load(listOf("https://a"), "vi")!!
        assertThat(result.fromCache).isTrue()
        assertThat(result.apps.single().packageName).isEqualTo("com.waze")
    }

    @Test
    fun garbageFromServerCountsAsFailure() {
        val result = StoreCatalog({ "<html>captive portal</html>" }, MemCache("https://a" to json))
            .load(listOf("https://a"), "vi")!!
        assertThat(result.fromCache).isTrue()
    }

    @Test
    fun noNetworkAndNoCacheIsNull() {
        assertThat(StoreCatalog({ null }, MemCache()).load(listOf("https://a"), "vi")).isNull()
    }

    @Test
    fun descriptionFollowsLanguageThenEnglish() {
        val vi = StoreCatalog({ json }, MemCache()).load(listOf("https://a"), "vi")!!
        val ru = StoreCatalog({ json }, MemCache()).load(listOf("https://a"), "ru")!!
        assertThat(vi.apps.single().description).isEqualTo("Dẫn đường")
        assertThat(ru.apps.single().description).isEqualTo("Navigation")
    }

    @Test
    fun relativeDownloadResolved() {
        assertThat(
            StoreCatalog.resolveUrl("https://a", "/apk/waze.apk")
        ).isEqualTo("https://a/apk/waze.apk")
    }

    @Test
    fun absoluteDownloadKept() {
        assertThat(
            StoreCatalog.resolveUrl("https://a", "https://x/y.apk")
        ).isEqualTo("https://x/y.apk")
    }

    @Test
    fun sha256CheckedAgainstFile() {
        val file = folder.newFile("f.apk").apply { writeText("hello") }
        val sum = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertThat(StoreCatalog.sha256Matches(file, sum)).isTrue()
        assertThat(StoreCatalog.sha256Matches(file, sum.uppercase())).isTrue()
        assertThat(StoreCatalog.sha256Matches(file, "00")).isFalse()
        assertThat(StoreCatalog.sha256Matches(file, "")).isTrue()
    }

    @Test
    fun fileCacheRoundTrips() {
        val cache = StoreCatalog.FileCatalogCache(File(folder.root, "cache"))
        assertThat(cache.read()).isNull()
        cache.write("https://a", json)
        assertThat(cache.read()).isEqualTo("https://a" to json)
    }
}
