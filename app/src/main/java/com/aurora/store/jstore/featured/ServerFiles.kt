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
}
