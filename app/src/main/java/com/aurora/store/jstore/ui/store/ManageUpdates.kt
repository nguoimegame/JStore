/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.aurora.store.jstore.ManagedApp
import com.aurora.store.jstore.PlayVersion

/**
 * Which installed apps have a newer build, and where it comes from, in the order JaecooApp's
 * app manager used: jstore from its GitHub releases, apps in the Jaecoo catalogue from the
 * catalogue, everything else from Google Play.
 */
object ManageUpdates {

    private const val JSTORE_PACKAGE = "com.example.jstore"
    private val VERSION_SPLITTER = Regex("[^0-9a-zA-Z]+")

    enum class Source { JSTORE, JAECOO, PLAY }

    data class Update(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val source: Source,
        val downloadUrl: String = "",
        val fileSize: String = "",
        val sha256: String = ""
    )

    /**
     * [catalog] plus the featured apps the server can serve, so the manager updates them from the
     * server instead of Google Play. The catalogue wins when both list a package.
     */
    fun withFeatured(
        catalog: StoreCatalog.Catalog?,
        featured: List<StoreCatalog.CatalogApp>
    ): StoreCatalog.Catalog? {
        if (catalog == null) return null
        val known = catalog.apps.map { it.packageName }.toMutableSet()
        val extra = featured.filter {
            it.downloadUrl.isNotEmpty() && it.packageName.isNotEmpty() && known.add(it.packageName)
        }
        return if (extra.isEmpty()) catalog else catalog.copy(apps = catalog.apps + extra)
    }

    /** [play] asks Google Play for the packages given; it blocks. */
    fun pick(
        apps: List<ManagedApp>,
        jstoreRelease: JstoreRelease?,
        catalog: StoreCatalog.Catalog?,
        play: (List<String>) -> List<PlayVersion>
    ): Map<String, Update> {
        val updates = mutableMapOf<String, Update>()
        jstoreUpdate(apps, jstoreRelease)?.let { updates[JSTORE_PACKAGE] = it }

        val others = apps.filter { it.packageName != JSTORE_PACKAGE }
        updates += catalogUpdates(others, catalog)

        val catalogPackages = catalog?.apps?.map {
            it.packageName
        }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val forPlay = others.filter { it.packageName !in catalogPackages }
        if (forPlay.isNotEmpty()) {
            updates +=
                playUpdates(forPlay, play(forPlay.map { it.packageName }))
        }
        return updates
    }

    private fun jstoreUpdate(apps: List<ManagedApp>, release: JstoreRelease?): Update? {
        val installed = apps.firstOrNull { it.packageName == JSTORE_PACKAGE } ?: return null
        if (release == null ||
            !isVersionNewer(release.versionName, installed.versionName)
        ) {
            return null
        }
        return Update(
            JSTORE_PACKAGE,
            release.versionName,
            0L,
            Source.JSTORE,
            release.apkUrl,
            sha256 = release.sha256
        )
    }

    private fun catalogUpdates(
        apps: List<ManagedApp>,
        catalog: StoreCatalog.Catalog?
    ): Map<String, Update> {
        if (catalog == null) return emptyMap()
        val byPackage = apps.associateBy { it.packageName }
        val updates = mutableMapOf<String, Update>()
        for (item in catalog.apps) {
            val installed = byPackage[item.packageName] ?: continue
            if (item.downloadUrl.isEmpty() ||
                !isVersionNewer(item.version, installed.versionName)
            ) {
                continue
            }
            updates[item.packageName] = Update(
                packageName = item.packageName,
                versionName = item.version,
                versionCode = 0L,
                source = Source.JAECOO,
                downloadUrl = StoreCatalog.resolveUrl(catalog.baseUrl, item.downloadUrl),
                fileSize = item.fileSize,
                sha256 = item.sha256
            )
        }
        return updates
    }

    private fun playUpdates(
        apps: List<ManagedApp>,
        versions: List<PlayVersion>
    ): Map<String, Update> {
        val byPackage = apps.associateBy { it.packageName }
        val updates = mutableMapOf<String, Update>()
        for (version in versions) {
            val installed = byPackage[version.packageName] ?: continue
            // A paid app cannot be downloaded this way: do not promise an update.
            if (!version.isFree || version.versionCode <= installed.versionCode) continue
            updates[version.packageName] =
                Update(version.packageName, version.versionName, version.versionCode, Source.PLAY)
        }
        return updates
    }

    /** Version names compared part by part, numbers as numbers; empty on either side is never newer. */
    fun isVersionNewer(serverVersion: String, installedVersion: String): Boolean {
        val server = serverVersion.trim().removePrefix("v").removePrefix("V")
        val installed = installedVersion.trim().removePrefix("v").removePrefix("V")
        if (server.isEmpty() ||
            installed.isEmpty() ||
            server.equals(installed, ignoreCase = true)
        ) {
            return false
        }
        val a = server.split(VERSION_SPLITTER).filter { it.isNotEmpty() }
        val b = installed.split(VERSION_SPLITTER).filter { it.isNotEmpty() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val p1 = a.getOrNull(i) ?: "0"
            val p2 = b.getOrNull(i) ?: "0"
            val n1 = p1.toLongOrNull()
            val n2 = p2.toLongOrNull()
            val order = if (n1 != null &&
                n2 != null
            ) {
                n1.compareTo(n2)
            } else {
                p1.compareTo(p2, ignoreCase = true)
            }
            if (order != 0) return order > 0
        }
        return false
    }
}
