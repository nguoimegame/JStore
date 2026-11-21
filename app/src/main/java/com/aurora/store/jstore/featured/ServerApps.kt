/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.EncodedCertificateSet
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.store.jstore.ui.store.ManageUpdates
import com.aurora.store.jstore.ui.store.StoreCatalog
import com.aurora.store.jstore.ui.store.StoreCatalog.CatalogApp
import com.aurora.store.jstore.ui.store.StoreOps

/** An app the Jaecoo server lists; [fromCatalogue] is true for `apps.json`, false for `featured.json`. */
data class ServerEntry(val app: CatalogApp, val fromCatalogue: Boolean)

/** What is installed on the device for a package. */
data class Installed(val versionName: String, val versionCode: Long, val certHashes: List<String>)

/** Merges server entries (catalogue and featured list) with what Play knows about the same packages. */
object ServerApps {

    /** Catalogue first, then featured; blank packages dropped, the first occurrence of a package wins. */
    fun entries(catalogue: List<CatalogApp>, featured: List<CatalogApp>): List<ServerEntry> =
        (catalogue.map { ServerEntry(it, true) } + featured.map { ServerEntry(it, false) })
            .filter { it.app.packageName.isNotBlank() }
            .distinctBy { it.app.packageName }

    /** The server ships (or vouches for) this app, as opposed to only naming a Play app. */
    fun isServerOwned(entry: ServerEntry): Boolean =
        entry.fromCatalogue || entry.app.downloadUrl.isNotEmpty()

    /**
     * The code the update pipeline compares: one above the installed code when the server version
     * is newer, the installed code otherwise (so a current app never loops as "updatable").
     */
    fun versionCode(serverVersion: String, playVersionCode: Long?, installed: Installed?): Long =
        when {
            installed == null -> playVersionCode ?: 1L
            ManageUpdates.isVersionNewer(serverVersion, installed.versionName) ->
                installed.versionCode + 1
            else -> installed.versionCode
        }

    fun toApp(entry: ServerEntry, baseUrl: String, play: App?, installed: Installed?): App? {
        val server = entry.app
        if (play == null && server.downloadUrl.isEmpty()) return null
        val pkg = server.packageName
        val base = play ?: App(packageName = pkg)
        val size = StoreOps.parseEstimatedBytes(server.fileSize)
        val withText = base.copy(
            displayName = server.name.ifBlank { base.displayName },
            iconArtwork = if (server.iconUrl.isBlank()) {
                base.iconArtwork
            } else {
                Artwork(url = StoreCatalog.resolveUrl(baseUrl, server.iconUrl))
            },
            shortDescription = server.description.ifBlank { base.shortDescription },
            description = server.description.ifBlank { base.description },
            versionName = server.version.ifBlank { base.versionName },
            size = if (server.fileSize.isBlank()) base.size else size,
            isInstalled = installed != null
        )
        if (server.downloadUrl.isEmpty()) return withText
        return withText.copy(
            fileList = listOf(
                PlayFile(
                    name = "$pkg.apk",
                    url = StoreCatalog.resolveUrl(baseUrl, server.downloadUrl),
                    size = size,
                    // DownloadWorker compares lowercase hex; the server may pad or uppercase it
                    sha256 = server.sha256.trim().lowercase()
                )
            ),
            versionCode = versionCode(server.version, play?.versionCode, installed),
            certificateSetList = installed?.certHashes
                ?.map { EncodedCertificateSet(certificateSet = it, sha256 = "") }
                ?: withText.certificateSetList
        )
    }

    fun assemble(
        entries: List<ServerEntry>,
        baseUrl: String,
        play: Map<String, App>,
        installed: (String) -> Installed?
    ): List<App> = entries.mapNotNull {
        val pkg = it.app.packageName
        toApp(it, baseUrl, play[pkg], installed(pkg))
    }
}
