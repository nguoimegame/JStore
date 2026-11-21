/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class JaecooInstallerTest {

    private fun names(files: List<File>) = files.map { it.name }

    @Test
    fun baseGoesFirstWhateverTheDirectoryOrder() {
        // The order Spotify's download folder listed on the emulator.
        val listed = listOf("config.mdpi.apk", "base.apk", "config.vi.apk", "config.x86_64.apk")
            .map { File("/d", it) }

        assertThat(names(JaecooInstaller.baseFirst(listed)))
            .containsExactly("base.apk", "config.mdpi.apk", "config.vi.apk", "config.x86_64.apk")
            .inOrder()
    }

    @Test
    fun singleApkStaysAsItIs() {
        assertThat(names(JaecooInstaller.baseFirst(listOf(File("/d", "base.apk")))))
            .containsExactly("base.apk")
    }

    @Test
    fun noBaseKeepsTheOrder() {
        val listed = listOf(File("/d", "config.vi.apk"), File("/d", "config.mdpi.apk"))

        assertThat(JaecooInstaller.baseFirst(listed)).isEqualTo(listed)
    }
}
