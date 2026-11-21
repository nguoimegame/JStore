/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.EncodedCertificateSet
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.store.jstore.ui.store.StoreCatalog.CatalogApp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerDetailsTest {

    private val pkg = "com.example.app"

    private val play = App(
        packageName = pkg,
        displayName = "Play name",
        iconArtwork = Artwork(url = "https://play/icon.png"),
        shortDescription = "Play short",
        description = "Play long",
        versionName = "1.0.0",
        versionCode = 10L,
        size = 100L,
        screenshots = listOf(Artwork(url = "https://play/shot.png")),
        labeledRating = "4.5",
        fileList = listOf(PlayFile(name = "base.apk", url = "https://play/base.apk")),
        certificateSetList = listOf(EncodedCertificateSet(certificateSet = "play", sha256 = "")),
        isInstalled = false
    )

    // The merged app from the last load; its install state is stale on purpose.
    private val server = App(
        packageName = pkg,
        displayName = "Server name",
        iconArtwork = Artwork(url = "https://server/icon.png"),
        shortDescription = "Server text",
        description = "Server text",
        versionName = "2.0.0",
        versionCode = 21L,
        size = 200L,
        fileList = listOf(PlayFile(name = "$pkg.apk", url = "https://server/app.apk")),
        certificateSetList = listOf(EncodedCertificateSet(certificateSet = "stale", sha256 = "")),
        isInstalled = true
    )

    private fun entry(downloadUrl: String, version: String = "2.0.0") = ServerEntry(
        CatalogApp(
            id = pkg,
            name = "Server name",
            description = "",
            iconUrl = "",
            category = "",
            version = version,
            packageName = pkg,
            downloadUrl = downloadUrl,
            fileSize = "",
            sha256 = ""
        ),
        fromCatalogue = true
    )

    private val fileEntry = entry("/apk/app.apk")
    private val textEntry = entry("")
    private val failed = Result.failure<App>(IllegalStateException("not on Play"))

    @Test
    fun playWithServerFileTakesServerTextAndFile() {
        val live = Installed("1.0.0", 10L, listOf("device"))
        val app = ServerDetails.resolve(Result.success(play), server, fileEntry, live)!!

        assertThat(app.displayName).isEqualTo("Server name")
        assertThat(app.iconArtwork.url).isEqualTo("https://server/icon.png")
        assertThat(app.shortDescription).isEqualTo("Server text")
        assertThat(app.description).isEqualTo("Server text")
        assertThat(app.versionName).isEqualTo("2.0.0")
        assertThat(app.size).isEqualTo(200L)
        assertThat(app.fileList).isEqualTo(server.fileList)
        assertThat(app.isInstalled).isTrue()
        assertThat(app.certificateSetList.map { it.certificateSet }).containsExactly("device")
        // Everything else stays Play's.
        assertThat(app.screenshots).isEqualTo(play.screenshots)
        assertThat(app.labeledRating).isEqualTo("4.5")
    }

    @Test
    fun playWithoutServerFileKeepsPlayFileAndInstallState() {
        val live = Installed("1.0.0", 10L, listOf("device"))
        val app = ServerDetails.resolve(Result.success(play), server, textEntry, live)!!

        assertThat(app.displayName).isEqualTo("Server name")
        assertThat(app.versionName).isEqualTo("2.0.0")
        assertThat(app.size).isEqualTo(200L)
        assertThat(app.fileList).isEqualTo(play.fileList)
        assertThat(app.versionCode).isEqualTo(10L)
        assertThat(app.certificateSetList).isEqualTo(play.certificateSetList)
        assertThat(app.isInstalled).isFalse()
        assertThat(app.screenshots).isEqualTo(play.screenshots)
    }

    @Test
    fun liveInstallBeatsStaleSnapshotSoNoUpdateLoop() {
        // Snapshot: not installed, versionCode 11. Live: server 2.0.0 already installed as code 10.
        val stale = server.copy(isInstalled = false, versionCode = 11L)
        val live = Installed("2.0.0", 10L, listOf("device"))

        val app = ServerDetails.resolve(Result.success(play), stale, fileEntry, live)!!

        assertThat(app.isInstalled).isTrue()
        assertThat(app.versionCode).isEqualTo(10L)
    }

    @Test
    fun liveInstallOlderThanServerIsOneAbove() {
        val live = Installed("1.5.0", 15L, listOf("device"))

        val app = ServerDetails.resolve(Result.success(play), server, fileEntry, live)!!

        assertThat(app.versionCode).isEqualTo(16L)
    }

    @Test
    fun notInstalledLiveUsesPlayVersionCode() {
        val app = ServerDetails.resolve(Result.success(play), server, fileEntry, null)!!

        assertThat(app.isInstalled).isFalse()
        assertThat(app.versionCode).isEqualTo(10L)
        assertThat(app.certificateSetList).isEqualTo(play.certificateSetList)
    }

    @Test
    fun notInstalledServerOnlyUsesOne() {
        val app = ServerDetails.resolve(failed, server, fileEntry, null)!!

        assertThat(app.isInstalled).isFalse()
        assertThat(app.versionCode).isEqualTo(1L)
    }

    @Test
    fun playOnlyWhenServerUnknown() {
        val live = Installed("1.0.0", 10L, listOf("device"))

        assertThat(ServerDetails.resolve(Result.success(play), null, null, live))
            .isSameInstanceAs(play)
    }

    @Test
    fun playFailsServerOnly() {
        val live = Installed("2.0.0", 21L, listOf("device"))
        val app = ServerDetails.resolve(failed, server, fileEntry, live)!!

        assertThat(app).isEqualTo(
            server.copy(
                certificateSetList = listOf(
                    EncodedCertificateSet(certificateSet = "device", sha256 = "")
                )
            )
        )
        assertThat(ServerDetails.isServerOnly(failed, server)).isTrue()
    }

    @Test
    fun blankPlayFallsBackToServer() {
        val blank = Result.success(App(packageName = pkg))

        val app = ServerDetails.resolve(blank, server, textEntry, null)!!

        assertThat(app).isEqualTo(server.copy(isInstalled = false))
        assertThat(ServerDetails.isServerOnly(blank, server)).isTrue()
    }

    @Test
    fun playUsableIsNotServerOnly() {
        assertThat(ServerDetails.isServerOnly(Result.success(play), server)).isFalse()
        assertThat(ServerDetails.isServerOnly(failed, null)).isFalse()
    }

    @Test
    fun bothMissingIsNull() {
        assertThat(ServerDetails.resolve(failed, null, null, null)).isNull()
    }

    @Test
    fun reResolveAfterInstallOfOlderServerBuildIsNotUpdatable() {
        // Play has 10; the server hosts the older 1.0.0, installed from the server as code 7.
        val olderEntry = entry("/apk/app.apk", version = "1.0.0")
        val before = ServerDetails.resolve(Result.success(play), server, olderEntry, null)!!
        val live = Installed("1.0.0", 7L, listOf("device"))

        val after = ServerDetails.resolve(Result.success(play), server, olderEntry, live)!!

        assertThat(before.versionCode).isEqualTo(10L)
        assertThat(after.versionCode).isEqualTo(7L)
        assertThat(after.isInstalled).isTrue()
        assertThat(after.certificateSetList.map { it.certificateSet }).containsExactly("device")
    }

    @Test
    fun playFailureFallsBackOnlyForServerFiles() {
        assertThat(ServerDetails.fallsBackOnPlayFailure(fileEntry, server)).isTrue()
        assertThat(ServerDetails.fallsBackOnPlayFailure(textEntry, server)).isFalse()
        assertThat(ServerDetails.fallsBackOnPlayFailure(null, server)).isFalse()
        assertThat(ServerDetails.fallsBackOnPlayFailure(fileEntry, null)).isFalse()
    }
}
