/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.google.common.truth.Truth.assertThat
import java.util.Properties
import org.junit.Test

class PlayDeviceProfileTest {

    @Test
    fun overridesSplitDeterminingPropertiesWithRealDeviceValues() {
        val base = Properties().apply {
            setProperty("Build.MODEL", "Pixel 9a")
            setProperty(
                "Build.FINGERPRINT",
                "google/tegu/tegu:15/BD4A.250405.003/13238919:user/release-keys"
            )
            setProperty("Platforms", "arm64-v8a")
            setProperty("Screen.Density", "420")
            setProperty("Screen.Width", "1080")
            setProperty("Screen.Height", "2424")
            setProperty("Build.VERSION.SDK_INT", "35")
            setProperty("Build.VERSION.RELEASE", "15")
            setProperty("Locales", "en_US,vi_VN")
            setProperty("Features", "android.hardware.touchscreen,android.hardware.wifi")
        }

        val result = PlayDeviceProfile.applyCarOverrides(
            base = base,
            abis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
            densityDpi = 240,
            widthPx = 1920,
            heightPx = 1080,
            sdkInt = 29,
            release = "10",
            deviceLocales = listOf("vi-VN", "en-US")
        )

        assertThat(result.getProperty("Build.MODEL")).isEqualTo("Pixel 9a")
        assertThat(result.getProperty("Platforms")).isEqualTo("arm64-v8a,armeabi-v7a,armeabi")
        assertThat(result.getProperty("Screen.Density")).isEqualTo("240")
        assertThat(result.getProperty("Screen.Width")).isEqualTo("1920")
        assertThat(result.getProperty("Screen.Height")).isEqualTo("1080")
        assertThat(result.getProperty("Build.VERSION.SDK_INT")).isEqualTo("29")
        assertThat(result.getProperty("Build.VERSION.RELEASE")).isEqualTo("10")
    }

    @Test
    fun stripsAutomotiveFeaturesAndEnsuresPhoneNavigationFeatures() {
        val base = Properties().apply {
            setProperty(
                "Features",
                "android.hardware.type.automotive,android.software.car.templates_host,android.hardware.wifi"
            )
        }

        val result = PlayDeviceProfile.applyCarOverrides(
            base = base,
            abis = listOf("arm64-v8a"),
            densityDpi = 240,
            widthPx = 1920,
            heightPx = 1080,
            sdkInt = 30,
            release = "11",
            deviceLocales = listOf("vi_VN")
        )

        val features = result.getProperty("Features").split(",")
        assertThat(features).doesNotContain("android.hardware.type.automotive")
        assertThat(features).doesNotContain("android.software.car.templates_host")
        assertThat(features).containsAtLeast(
            "android.hardware.location.gps",
            "android.hardware.location",
            "android.hardware.telephony",
            "android.hardware.wifi",
            "android.hardware.microphone",
            "android.hardware.bluetooth",
            "android.hardware.touchscreen"
        )
    }

    @Test
    fun mergesDeviceLocalesFirstWithoutDuplicates() {
        val base = Properties().apply {
            setProperty("Locales", "en_US,fr_FR")
        }

        val result = PlayDeviceProfile.applyCarOverrides(
            base = base,
            abis = listOf("arm64-v8a"),
            densityDpi = 240,
            widthPx = 1920,
            heightPx = 1080,
            sdkInt = 30,
            release = "11",
            deviceLocales = listOf("vi-VN", "en-US")
        )

        val locales = result.getProperty("Locales").split(",")
        assertThat(locales.take(3)).containsExactly("vi_VN", "en_US", "fr_FR").inOrder()
    }
}
