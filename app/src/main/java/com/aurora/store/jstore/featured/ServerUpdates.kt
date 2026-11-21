/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App

/**
 * Update rules for packages the Jaecoo server owns: Play is never asked about them, and
 * an update exists only when the server offers a newer APK than the installed one.
 */
object ServerUpdates {

    /** The packages to look up on Play: [packages] without the server-owned ones. */
    fun forPlay(packages: List<String>, owned: Set<String>): List<String> =
        packages.filterNot { it in owned }

    /**
     * Server updates for [owned]: a package is included iff it is installed, its merged
     * app has a server APK in `fileList`, and that app's versionCode is above the
     * installed one.
     */
    fun updates(
        owned: Set<String>,
        appOf: (String) -> App?,
        installed: (String) -> Installed?
    ): List<App> = owned.mapNotNull { pkg ->
        val current = installed(pkg) ?: return@mapNotNull null
        val app = appOf(pkg) ?: return@mapNotNull null
        app.takeIf { it.fileList.isNotEmpty() && it.versionCode > current.versionCode }
    }
}
