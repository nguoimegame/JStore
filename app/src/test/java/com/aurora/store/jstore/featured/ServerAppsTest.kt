/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.store.jstore.ui.store.StoreCatalog
import com.aurora.store.jstore.ui.store.StoreCatalog.CatalogApp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerAppsTest {

    private fun entry(
        pkg: String,
        name: String = "",
        downloadUrl: String = "",
        version: String = "",
        sha256: String = "",
        iconUrl: String = ""
    ) = CatalogApp(
        id = pkg,
        name = name,
        description = "",
        iconUrl = iconUrl,
        category = "",
        version = version,
        packageName = pkg,
        downloadUrl = downloadUrl,
        fileSize = "",
        sha256 = sha256
    )

    private fun serverEntry(app: CatalogApp, fromCatalogue: Boolean = false) =
        ServerEntry(app, fromCatalogue)

    @Test
    fun entriesPutCatalogueFirstAndDedupe() {
        val result = ServerApps.entries(
            catalogue = listOf(entry("a"), entry("b")),
            featured = listOf(entry("b"), entry("c"), entry(""))
        )
        assertThat(result.map { it.app.packageName }).containsExactly("a", "b", "c").inOrder()
        assertThat(result.first { it.app.packageName == "b" }.fromCatalogue).isTrue()
    }

    @Test
    fun ownership() {
        val withUrl = entry("a", downloadUrl = "/a.apk")
        assertThat(ServerApps.isServerOwned(serverEntry(withUrl, fromCatalogue = true))).isTrue()
        assertThat(ServerApps.isServerOwned(serverEntry(withUrl))).isTrue()
        assertThat(ServerApps.isServerOwned(serverEntry(entry("a")))).isFalse()
    }

    @Test
    fun catalogueEntryWithoutUrlIsPlayOwned() {
        assertThat(ServerApps.isServerOwned(serverEntry(entry("a"), fromCatalogue = true)))
            .isFalse()
    }

    @Test
    fun versionCodeNotInstalled() {
        assertThat(ServerApps.versionCode("2.0", 50, null)).isEqualTo(50)
        assertThat(ServerApps.versionCode("2.0", null, null)).isEqualTo(1)
    }

    @Test
    fun versionCodeNewerOnServer() {
        assertThat(
            ServerApps.versionCode("2.0", 50, Installed("1.9", 40, emptyList()))
        ).isEqualTo(41)
    }

    @Test
    fun versionCodeNotNewer() {
        assertThat(
            ServerApps.versionCode("2.0", 50, Installed("2.0", 40, emptyList()))
        ).isEqualTo(40)
    }

    @Test
    fun serverFieldsWinPlayFillsGaps() {
        val play = App(packageName = "com.waze", displayName = "Waze Play", versionName = "5.0")
        val app = ServerApps.toApp(
            serverEntry(entry("com.waze", name = "Waze HU", version = "")),
            "https://s",
            play,
            null
        )!!
        assertThat(app.displayName).isEqualTo("Waze HU")
        assertThat(app.versionName).isEqualTo("5.0")
    }

    @Test
    fun nameOnlyEntryKeepsPlayName() {
        val parsed = StoreCatalog.parse(
            """[{"packageName":"com.spotify.music"}]""",
            "en",
            defaultName = ""
        )
        val play = App(packageName = "com.spotify.music", displayName = "Spotify")
        val app = ServerApps.toApp(serverEntry(parsed.single()), "https://s", play, null)!!
        assertThat(app.displayName).isEqualTo("Spotify")
    }

    @Test
    fun downloadUrlReplacesFileList() {
        val play = App(packageName = "com.waze")
        val app = ServerApps.toApp(
            serverEntry(entry("com.waze", downloadUrl = "/apk/w.apk", sha256 = "ab")),
            "https://s",
            play,
            null
        )!!
        assertThat(app.fileList).hasSize(1)
        assertThat(app.fileList.single().url).isEqualTo("https://s/apk/w.apk")
        assertThat(app.fileList.single().sha256).isEqualTo("ab")
    }

    @Test
    fun serverHashIsTrimmedAndLowercased() {
        val app = ServerApps.toApp(
            serverEntry(entry("com.waze", downloadUrl = "/apk/w.apk", sha256 = "  ABCDEF01 ")),
            "https://s",
            null,
            null
        )!!
        assertThat(app.fileList.single().sha256).isEqualTo("abcdef01")
    }

    @Test
    fun blankServerHashStaysBlank() {
        val app = ServerApps.toApp(
            serverEntry(entry("com.waze", downloadUrl = "/apk/w.apk", sha256 = "   ")),
            "https://s",
            null,
            null
        )!!
        assertThat(app.fileList.single().sha256).isEmpty()
    }

    @Test
    fun urlEntryWithoutPlayIsFree() {
        val app = ServerApps.toApp(
            serverEntry(entry("com.litube", downloadUrl = "/apk/litube.apk")),
            "https://s",
            null,
            null
        )!!
        assertThat(app.isFree).isTrue()
    }

    @Test
    fun urlEntryPaidOnPlayIsFree() {
        val play = App(packageName = "com.waze", isFree = false, price = "50.000 đ")
        val app = ServerApps.toApp(
            serverEntry(entry("com.waze", downloadUrl = "/apk/w.apk")),
            "https://s",
            play,
            null
        )!!
        assertThat(app.isFree).isTrue()
    }

    @Test
    fun urlLessEntryKeepsPlayIsFree() {
        val paid = App(packageName = "com.waze", isFree = false, price = "50.000 đ")
        val free = App(packageName = "com.waze", isFree = true)
        val entry = serverEntry(entry("com.waze", name = "Waze"))
        assertThat(ServerApps.toApp(entry, "https://s", paid, null)!!.isFree).isFalse()
        assertThat(ServerApps.toApp(entry, "https://s", free, null)!!.isFree).isTrue()
    }

    @Test
    fun hashlessDownloadStillProducesFile() {
        val app = ServerApps.toApp(
            serverEntry(entry("com.waze", downloadUrl = "/apk/w.apk")),
            "https://s",
            null,
            null
        )!!
        assertThat(app.fileList).hasSize(1)
        assertThat(app.fileList.single().sha256).isEqualTo("")
    }

    @Test
    fun dropsWhenNoPlayAndNoUrl() {
        assertThat(
            ServerApps.toApp(serverEntry(entry("com.waze")), "https://s", null, null)
        ).isNull()
    }

    @Test
    fun serverOnlyWithoutPlay() {
        val app = ServerApps.assemble(
            listOf(serverEntry(entry("com.waze", name = "Waze HU", downloadUrl = "/w.apk"))),
            "https://s",
            emptyMap()
        ) { null }.single()
        assertThat(app.displayName).isEqualTo("Waze HU")
        assertThat(app.packageName).isEqualTo("com.waze")
    }

    @Test
    fun urlLessEntryLeavesPlayFilesAndVersionCode() {
        val play = App(packageName = "com.x", versionCode = 7, displayName = "X")
        val installed = Installed("1.0", 3, listOf("cert"))
        val app = ServerApps.toApp(
            serverEntry(entry("com.x", version = "9.0")),
            "https://s",
            play,
            installed
        )!!
        assertThat(app.versionCode).isEqualTo(7)
        assertThat(app.fileList).isEmpty()
        assertThat(app.certificateSetList).isEmpty()
        assertThat(app.isInstalled).isTrue()
    }

    @Test
    fun assembleKeepsOrder() {
        val entries = listOf("c", "a", "b").map { serverEntry(entry(it, downloadUrl = "/$it.apk")) }
        val result = ServerApps.assemble(entries, "https://s", emptyMap()) { null }
        assertThat(result.map { it.packageName }).containsExactly("c", "a", "b").inOrder()
    }
}
