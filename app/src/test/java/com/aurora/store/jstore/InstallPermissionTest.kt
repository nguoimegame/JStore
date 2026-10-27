/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.aurora.store.data.model.Installer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstallPermissionTest {

    @Test
    fun jaecooInstallsWithoutTheUnknownAppsPermission() {
        assertThat(needsUnknownAppsPermission(Installer.JAECOO, jaecooAvailable = true)).isFalse()
    }

    @Test
    fun jaecooChosenButMissingFallsBackToTheSystemInstaller() {
        assertThat(needsUnknownAppsPermission(Installer.JAECOO, jaecooAvailable = false)).isTrue()
    }

    @Test
    fun otherInstallersStillNeedThePermissionEvenWithJaecooAround() {
        for (installer in Installer.entries) {
            if (installer != Installer.JAECOO) {
                assertThat(needsUnknownAppsPermission(installer, jaecooAvailable = true)).isTrue()
                assertThat(needsUnknownAppsPermission(installer, jaecooAvailable = false)).isTrue()
            }
        }
    }

    @Test
    fun unsetPreferenceDefaultsToJaecooWhenAvailable() {
        assertThat(resolveInstaller(savedOrdinal = null, jaecooAvailable = true))
            .isEqualTo(Installer.JAECOO)
    }

    @Test
    fun unsetPreferenceDefaultsToSessionWhenJaecooUnavailable() {
        assertThat(resolveInstaller(savedOrdinal = null, jaecooAvailable = false))
            .isEqualTo(Installer.SESSION)
    }

    @Test
    fun explicitPreferenceIsRespected() {
        val session = resolveInstaller(Installer.SESSION.ordinal, jaecooAvailable = true)
        assertThat(session).isEqualTo(Installer.SESSION)

        val jaecoo = resolveInstaller(Installer.JAECOO.ordinal, jaecooAvailable = true)
        assertThat(jaecoo).isEqualTo(Installer.JAECOO)

        val shizuku = resolveInstaller(Installer.SHIZUKU.ordinal, jaecooAvailable = true)
        assertThat(shizuku).isEqualTo(Installer.SHIZUKU)
    }

    @Test
    fun invalidPreferenceOrdinalFallsBackToDefault() {
        assertThat(resolveInstaller(savedOrdinal = 999, jaecooAvailable = true))
            .isEqualTo(Installer.JAECOO)
        assertThat(resolveInstaller(savedOrdinal = -1, jaecooAvailable = false))
            .isEqualTo(Installer.SESSION)
    }
}
