/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App

/** Picks what the details screen shows when the Jaecoo server also lists the app. */
object ServerDetails {

    /**
     * [play] is the Play lookup, [server] the merged server app (null when the server does not list
     * the package), [hasServerFile] whether the server entry has a `downloadUrl`.
     *
     * - Play usable and [server] known: the server's text fields win; the file, `versionCode`,
     *   certificates and `isInstalled` come from [server] only when [hasServerFile]; the rest is Play's.
     * - [server] unknown: Play as is (null when Play failed).
     * - Play failed or blank and [server] known: [server].
     */
    fun resolve(play: Result<App>, server: App?, hasServerFile: Boolean): App? {
        val playApp = play.getOrNull()
        if (server == null) return playApp
        if (playApp == null || playApp.displayName.isBlank()) return server
        val withText = playApp.copy(
            displayName = server.displayName,
            iconArtwork = server.iconArtwork,
            shortDescription = server.shortDescription,
            description = server.description,
            versionName = server.versionName,
            size = server.size
        )
        if (!hasServerFile) return withText
        return withText.copy(
            fileList = server.fileList,
            versionCode = server.versionCode,
            certificateSetList = server.certificateSetList,
            isInstalled = server.isInstalled
        )
    }
}
