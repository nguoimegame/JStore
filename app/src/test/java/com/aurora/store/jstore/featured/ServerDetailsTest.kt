/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.EncodedCertificateSet
import com.aurora.gplayapi.data.models.PlayFile
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
        certificateSetList = listOf(EncodedCertificateSet(certificateSet = "device", sha256 = "")),
        isInstalled = true
    )

    @Test
    fun playWithServerFileTakesServerTextAndFile() {
        val app = ServerDetails.resolve(Result.success(play), server, hasServerFile = true)!!

        assertThat(app.displayName).isEqualTo("Server name")
        assertThat(app.iconArtwork.url).isEqualTo("https://server/icon.png")
        assertThat(app.shortDescription).isEqualTo("Server text")
        assertThat(app.description).isEqualTo("Server text")
        assertThat(app.versionName).isEqualTo("2.0.0")
        assertThat(app.size).isEqualTo(200L)
        assertThat(app.fileList).isEqualTo(server.fileList)
        assertThat(app.versionCode).isEqualTo(21L)
        assertThat(app.certificateSetList).isEqualTo(server.certificateSetList)
        assertThat(app.isInstalled).isTrue()
        // Everything else stays Play's.
        assertThat(app.screenshots).isEqualTo(play.screenshots)
        assertThat(app.labeledRating).isEqualTo("4.5")
    }

    @Test
    fun playWithoutServerFileKeepsPlayFileAndVersionCode() {
        val app = ServerDetails.resolve(Result.success(play), server, hasServerFile = false)!!

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
    fun playOnlyWhenServerUnknown() {
        assertThat(ServerDetails.resolve(Result.success(play), null, hasServerFile = false))
            .isSameInstanceAs(play)
    }

    @Test
    fun playFailsServerOnly() {
        val failed = Result.failure<App>(IllegalStateException("not on Play"))

        assertThat(ServerDetails.resolve(failed, server, hasServerFile = true))
            .isSameInstanceAs(server)
    }

    @Test
    fun blankPlayFallsBackToServer() {
        val blank = Result.success(App(packageName = pkg))

        assertThat(ServerDetails.resolve(blank, server, hasServerFile = false))
            .isSameInstanceAs(server)
    }

    @Test
    fun bothMissingIsNull() {
        val failed = Result.failure<App>(IllegalStateException("not on Play"))

        assertThat(ServerDetails.resolve(failed, null, hasServerFile = false)).isNull()
    }
}
