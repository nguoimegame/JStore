/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The Jaecoo server cache (`apps.json`, `server.txt`, `featured.json`) shared by the Kho tab and
 * [com.aurora.store.jstore.featured.JaecooServerApps]; both write it, so every write is atomic.
 */
object StoreCache {

    const val DIR = "jstore_store"
    const val FEATURED_FILE = "featured.json"

    fun dir(cacheDir: File): File = File(cacheDir, DIR)

    fun featuredFile(cacheDir: File): File = File(dir(cacheDir), FEATURED_FILE)

    /**
     * Writes [text] to a temp file next to [target], then moves it over [target] in one step, so
     * a failed or concurrent write never leaves a partial file behind. The temp file is removed
     * when the write fails.
     */
    @Throws(IOException::class)
    fun writeAtomically(target: File, text: String) {
        val dir = target.absoluteFile.parentFile ?: throw IOException("No directory for $target")
        dir.mkdirs()
        val temp = File.createTempFile(target.name, ".tmp", dir)
        try {
            temp.writeText(text)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            temp.delete()
        }
    }
}
