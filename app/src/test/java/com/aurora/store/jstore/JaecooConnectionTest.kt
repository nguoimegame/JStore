/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JaecooConnectionTest {

    @Test
    fun statusesMapToResults() {
        assertThat(JaecooConnection.toResult(0, "")).isEqualTo(JaecooConnection.Result.Installed)
        assertThat(JaecooConnection.toResult(1, "")).isEqualTo(JaecooConnection.Result.Launched)
        assertThat(JaecooConnection.toResult(3, "")).isEqualTo(JaecooConnection.Result.Uninstalled)
    }

    @Test
    fun failureKeepsJaecooAppsMessage() {
        assertThat(JaecooConnection.toResult(2, "INSTALL_FAILED_OLDER_SDK"))
            .isEqualTo(JaecooConnection.Result.Failed("INSTALL_FAILED_OLDER_SDK"))
        // An unknown status from a newer JaecooApp is a failure, never a silent success.
        assertThat(
            JaecooConnection.toResult(42, "?")
        ).isEqualTo(JaecooConnection.Result.Failed("?"))
    }
}
