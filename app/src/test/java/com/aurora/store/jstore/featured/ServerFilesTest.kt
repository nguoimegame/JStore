/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.PlayFile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerFilesTest {

    private val serverUrl = "https://server/app.apk"
    private val isServer: (String) -> Boolean = { it == serverUrl }

    @Test
    fun hashlessServerFileAccepted() {
        val file = PlayFile(name = "app.apk", url = serverUrl)
        assertThat(ServerFiles.acceptUnverified(file, isServer)).isTrue()
    }

    @Test
    fun hashlessPlayFileRejected() {
        val file = PlayFile(name = "base.apk", url = "https://play/base.apk")
        assertThat(ServerFiles.acceptUnverified(file, isServer)).isFalse()
    }

    @Test
    fun fileWithHashNotShortCircuited() {
        val withSha256 = PlayFile(name = "app.apk", url = serverUrl, sha256 = "abc")
        val withSha1 = PlayFile(name = "app.apk", url = serverUrl, sha1 = "abc")
        assertThat(ServerFiles.acceptUnverified(withSha256, isServer)).isFalse()
        assertThat(ServerFiles.acceptUnverified(withSha1, isServer)).isFalse()
    }
}
