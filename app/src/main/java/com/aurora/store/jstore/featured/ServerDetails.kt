/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.EncodedCertificateSet

/** Picks what the details screen shows when the Jaecoo server also lists the app. */
object ServerDetails {

    /** True when Play gave nothing usable but the server knows the app. */
    fun isServerOnly(play: Result<App>, server: App?): Boolean =
        server != null && play.getOrNull()?.displayName.isNullOrBlank()

    /**
     * When Play fails, the server copy is shown only for an app the server ships ([entry] has a
     * `downloadUrl`). Otherwise the failure is handled as upstream does (an expired token goes
     * back to Splash), since a Play-owned copy from the last load would be stale and has no file.
     */
    fun fallsBackOnPlayFailure(entry: ServerEntry?, server: App?): Boolean =
        server != null && !entry?.app?.downloadUrl.isNullOrEmpty()

    /**
     * [play] is the Play lookup, [server] the merged server app from the last load (null when the
     * server does not list the package), [entry] its server entry, [installed] what is installed
     * right now (read at fetch time, never from the load's snapshot).
     *
     * - [server] unknown: Play as is (null when Play failed).
     * - Play usable and [server] known: the server's text fields win; the rest is Play's. When the
     *   entry has a `downloadUrl`, the server file is used and `isInstalled`, `versionCode` and the
     *   certificates are worked out again from [installed] (as [ServerApps.versionCode]), and the
     *   app is free (no Play purchase is involved).
     * - Play failed or blank and [server] known: [server], its install state from [installed].
     */
    fun resolve(play: Result<App>, server: App?, entry: ServerEntry?, installed: Installed?): App? {
        val playApp = play.getOrNull()
        if (server == null) return playApp
        val fileEntry = entry?.takeIf { it.app.downloadUrl.isNotEmpty() }
        if (isServerOnly(play, server)) {
            val app = server.copy(isInstalled = installed != null)
            return fileEntry?.let { app.withInstallState(it, null, installed) } ?: app
        }
        val withText = playApp!!.copy(
            displayName = server.displayName,
            iconArtwork = server.iconArtwork,
            shortDescription = server.shortDescription,
            description = server.description,
            versionName = server.versionName,
            size = server.size
        )
        if (fileEntry == null) return withText
        return withText.copy(fileList = server.fileList)
            .withInstallState(fileEntry, playApp.versionCode, installed)
    }

    private fun App.withInstallState(
        entry: ServerEntry,
        playVersionCode: Long?,
        installed: Installed?
    ): App = copy(
        isFree = true,
        isInstalled = installed != null,
        versionCode = ServerApps.versionCode(entry.app.version, playVersionCode, installed),
        certificateSetList = installed?.certHashes
            ?.map { EncodedCertificateSet(certificateSet = it, sha256 = "") }
            ?: certificateSetList
    )
}
