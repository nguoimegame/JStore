/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.PlayFile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerUpdatesTest {

    private val pkg = "com.example.server"

    private fun app(versionCode: Long, withFile: Boolean = true) = App(
        packageName = pkg,
        displayName = "Server app",
        versionCode = versionCode,
        fileList = if (withFile) {
            listOf(PlayFile(name = "$pkg.apk", url = "https://server/app.apk"))
        } else {
            emptyList()
        }
    )

    private fun installed(versionCode: Long) = Installed("1.0", versionCode, listOf("cert"))

    @Test
    fun ownedPackagesNotSentToPlay() {
        val forPlay = ServerUpdates.forPlay(listOf("a", pkg, "b"), setOf(pkg))

        assertThat(forPlay).containsExactly("a", "b").inOrder()
    }

    @Test
    fun newerServerVersionIsUpdate() {
        val updates = ServerUpdates.updates(
            owned = setOf(pkg),
            appOf = { app(41) },
            installed = { installed(40) }
        )

        assertThat(updates.map { it.packageName }).containsExactly(pkg)
    }

    @Test
    fun sameVersionIsNotUpdate() {
        val updates = ServerUpdates.updates(
            owned = setOf(pkg),
            appOf = { app(40) },
            installed = { installed(40) }
        )

        assertThat(updates).isEmpty()
    }

    @Test
    fun ownedWithoutDownloadUrlNoUpdate() {
        val updates = ServerUpdates.updates(
            owned = setOf(pkg),
            appOf = { app(41, withFile = false) },
            installed = { installed(40) }
        )

        assertThat(updates).isEmpty()
    }

    @Test
    fun notInstalledNoUpdate() {
        val updates = ServerUpdates.updates(
            owned = setOf(pkg),
            appOf = { app(41) },
            installed = { null }
        )

        assertThat(updates).isEmpty()
    }
}
