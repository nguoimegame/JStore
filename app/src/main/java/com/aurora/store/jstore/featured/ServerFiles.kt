/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.PlayFile

/** Download-verification exception for files served by the Jaecoo server. */
object ServerFiles {

    /**
     * True only for a file that carries no hash at all and comes from the Jaecoo server.
     * Play files, and any file with a sha256 or sha1, still go through the normal digest check.
     */
    fun acceptUnverified(file: PlayFile, isServerUrl: (String) -> Boolean): Boolean =
        file.sha256.isBlank() && file.sha1.isBlank() && isServerUrl(file.url)

    /**
     * True when a file already on disk may skip the download because its hash still matches.
     * A hashless file never may: the only hashless files that verify are the Jaecoo server's,
     * and their path is keyed by a synthetic versionCode that stays the same when the server
     * publishes a new version, so an old file there could be an older APK.
     */
    fun mayReuseExisting(file: PlayFile): Boolean =
        file.sha256.isNotBlank() || file.sha1.isNotBlank()

    /**
     * True when a 403/410 on [file] should drop the file list so the retry buys fresh Play URLs.
     * A Jaecoo server file has no Play purchase behind it: re-buying would fail, or fetch a
     * different Play APK under the synthetic versionCode.
     */
    fun repurchaseOnExpiredUrl(file: PlayFile, isServerUrl: (String) -> Boolean): Boolean =
        !isServerUrl(file.url)
}
