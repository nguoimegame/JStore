/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

/** One row of the Jaecoo catalogue, with what the screen knows about it on this head unit. */
data class StoreAppItem(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String,
    val category: String = "",
    val version: String = "",
    val packageName: String = "",
    val downloadUrl: String = "",
    val fileSize: String = "",
    /** Hex SHA-256 of the file at [downloadUrl]; empty when the catalogue gives none. */
    val sha256: String = "",
    var isInstalled: Boolean = false,
    var installedVersion: String? = null,
    var hasUpdate: Boolean = false,
    var downloadProgress: Int = 0,
    var isDownloading: Boolean = false
) {
    companion object {
        fun from(app: StoreCatalog.CatalogApp) = StoreAppItem(
            id = app.id,
            name = app.name,
            description = app.description,
            iconUrl = app.iconUrl,
            category = app.category,
            version = app.version,
            packageName = app.packageName,
            downloadUrl = app.downloadUrl,
            fileSize = app.fileSize,
            sha256 = app.sha256
        )
    }
}
