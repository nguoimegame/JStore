/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.content.Context
import android.os.Build
import java.util.Properties

/**
 * Builds the default Play device configuration for automotive head units.
 *
 * Uncertified head units (e.g. Jaecoo J7) report automotive features
 * (`android.hardware.type.automotive`) and incomplete phone features/libraries, causing Google Play
 * to reject standard phone/navigation apps with `AppNotSupported` (`status = 2`) and return `v (0)`
 * on the app details page.
 *
 * We start from a certified phone profile (`gplayapi_px_9a`) so Play's device-certification and
 * feature/library checks pass, then override the split-selection properties (`Platforms`,
 * `Screen.*`, `Build.VERSION.*`, `Locales`) with the head unit's actual values so Play still serves
 * APK splits matching the car's CPU ABI, screen density, Android SDK level, and locales.
 */
object PlayDeviceProfile {

    private val ESSENTIAL_PHONE_FEATURES = listOf(
        "android.hardware.location.gps",
        "android.hardware.location",
        "android.hardware.telephony",
        "android.hardware.wifi",
        "android.hardware.microphone",
        "android.hardware.bluetooth",
        "android.hardware.touchscreen"
    )

    private val AUTOMOTIVE_FEATURE_PREFIXES = listOf(
        "android.hardware.type.automotive",
        "android.software.car.",
        "com.google.android.feature.AUTOMOTIVE"
    )

    fun build(context: Context): Properties {
        val base = Properties().apply {
            context.resources
                .openRawResource(com.aurora.gplayapi.R.raw.gplayapi_px_9a)
                .use { load(it) }
        }
        val metrics = context.resources.displayMetrics
        val deviceLocales = context.assets.locales.filterNotNull()

        return applyCarOverrides(
            base = base,
            abis = Build.SUPPORTED_ABIS.toList(),
            densityDpi = metrics.densityDpi,
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE ?: "10",
            deviceLocales = deviceLocales
        )
    }

    internal fun applyCarOverrides(
        base: Properties,
        abis: List<String>,
        densityDpi: Int,
        widthPx: Int,
        heightPx: Int,
        sdkInt: Int,
        release: String,
        deviceLocales: List<String>
    ): Properties {
        val result = Properties().apply { putAll(base) }

        if (abis.isNotEmpty()) {
            result.setProperty("Platforms", abis.joinToString(","))
        }
        result.setProperty("Screen.Density", densityDpi.toString())
        result.setProperty("Screen.Width", widthPx.toString())
        result.setProperty("Screen.Height", heightPx.toString())
        result.setProperty("Build.VERSION.SDK_INT", sdkInt.toString())
        result.setProperty("Build.VERSION.RELEASE", release)

        val baseLocales = result.getProperty("Locales", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val normalizedDeviceLocales = deviceLocales
            .map { it.trim().replace("-", "_") }
            .filter { it.isNotEmpty() }
        val mergedLocales = (normalizedDeviceLocales + baseLocales).distinct()
        if (mergedLocales.isNotEmpty()) {
            result.setProperty("Locales", mergedLocales.joinToString(","))
        }

        val baseFeatures = result.getProperty("Features", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { feature ->
                AUTOMOTIVE_FEATURE_PREFIXES.any { prefix -> feature.startsWith(prefix) }
            }
        val mergedFeatures = (baseFeatures + ESSENTIAL_PHONE_FEATURES).distinct()
        result.setProperty("Features", mergedFeatures.joinToString(","))

        return result
    }
}
