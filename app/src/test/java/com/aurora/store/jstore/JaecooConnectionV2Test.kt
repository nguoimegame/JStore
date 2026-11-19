/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JaecooConnectionV2Test {

    @Test
    fun managerNeedsInstallerV2() {
        assertThat(JaecooConnection.supportsManager(null)).isFalse()
        assertThat(JaecooConnection.supportsManager(1)).isFalse()
        assertThat(JaecooConnection.supportsManager(2)).isTrue()
    }

    @Test
    fun installingStillWorksWithInstallerV1() {
        assertThat(JaecooConnection.INSTALLER_API_VERSION_MIN).isEqualTo(1)
    }

    @Test
    fun updatesServiceStaysV1() {
        assertThat(JaecooConnection.UPDATES_API_VERSION).isEqualTo(1)
    }

    @Test
    fun storeUiIsV1() {
        assertThat(JaecooConnection.STORE_UI_API_VERSION).isEqualTo(1)
    }
}
