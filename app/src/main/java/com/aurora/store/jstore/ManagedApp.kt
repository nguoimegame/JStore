/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.os.Bundle

/** One row of IJaecooInstaller.listApps: an app the driver can hide, show or remove. */
data class ManagedApp(
    val packageName: String,
    val label: String,
    val versionCode: Long,
    val versionName: String,
    val hidden: Boolean,
    /** JaecooApp refuses to hide or remove these (jstore itself, JaecooApp). */
    val protected: Boolean,
    /** PNG of the icon, only for hidden apps: to other apps they look uninstalled. */
    val icon: ByteArray?
) {
    companion object {
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_LABEL = "label"
        private const val KEY_VERSION_CODE = "versionCode"
        private const val KEY_VERSION_NAME = "versionName"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_PROTECTED = "protected"
        private const val KEY_ICON = "icon"

        /** Null when the row has no package name. */
        fun fromMap(row: Map<String, Any?>): ManagedApp? {
            val packageName =
                (row[KEY_PACKAGE_NAME] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return ManagedApp(
                packageName = packageName,
                label = (row[KEY_LABEL] as? String)?.takeIf { it.isNotEmpty() } ?: packageName,
                versionCode = (row[KEY_VERSION_CODE] as? Long) ?: 0L,
                versionName = row[KEY_VERSION_NAME] as? String ?: "",
                hidden = row[KEY_HIDDEN] as? Boolean ?: false,
                protected = row[KEY_PROTECTED] as? Boolean ?: false,
                icon = row[KEY_ICON] as? ByteArray
            )
        }

        fun fromBundle(bundle: Bundle): ManagedApp? = fromMap(
            buildMap {
                for (key in bundle.keySet()) {
                    @Suppress("DEPRECATION")
                    put(key, bundle.get(key))
                }
            }
        )
    }
}
