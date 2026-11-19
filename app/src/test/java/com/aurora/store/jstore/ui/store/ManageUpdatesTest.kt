/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.aurora.store.jstore.ManagedApp
import com.aurora.store.jstore.PlayVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManageUpdatesTest {

    private fun app(packageName: String, versionName: String, versionCode: Long = 1) =
        ManagedApp(packageName, packageName, versionCode, versionName, false, false, null)

    private fun catalogApp(packageName: String, version: String) = StoreCatalog.CatalogApp(
        id = packageName,
        name = packageName,
        description = "",
        iconUrl = "",
        category = "",
        version = version,
        packageName = packageName,
        downloadUrl = "/apk/$packageName.apk",
        fileSize = "",
        sha256 = "ab"
    )

    @Test
    fun jstoreComesOnlyFromGithub() {
        val updates = ManageUpdates.pick(
            apps = listOf(app("com.example.jstore", "1.0.0")),
            jstoreRelease = JstoreRelease("1.1.0", "https://x/jstore.apk", "s"),
            catalog = null,
            play = { emptyList() }
        )
        assertThat(
            updates.getValue("com.example.jstore").source
        ).isEqualTo(ManageUpdates.Source.JSTORE)
    }

    @Test
    fun jstoreIsNotAskedFromPlay() {
        val asked = mutableListOf<String>()
        ManageUpdates.pick(listOf(app("com.example.jstore", "1.0.0")), null, null) {
            asked += it
            emptyList()
        }
        assertThat(asked).isEmpty()
    }

    @Test
    fun catalogAppUpdatesFromCatalogAndIsNotAskedFromPlay() {
        val asked = mutableListOf<String>()
        val catalog = StoreCatalog.Catalog(
            "https://a",
            listOf(catalogApp("com.waze", "6.0")),
            false
        )
        val updates = ManageUpdates.pick(
            listOf(app("com.waze", "5.0"), app("x.y", "1")),
            null,
            catalog
        ) {
            asked += it
            emptyList()
        }
        assertThat(asked).containsExactly("x.y")
        val waze = updates.getValue("com.waze")
        assertThat(waze.source).isEqualTo(ManageUpdates.Source.JAECOO)
        assertThat(waze.downloadUrl).isEqualTo("https://a/apk/com.waze.apk")
    }

    @Test
    fun playAppUpdatesByVersionCode() {
        val updates = ManageUpdates.pick(listOf(app("x.y", "1", 1)), null, null) {
            listOf(PlayVersion("x.y", 9, "2", isFree = true))
        }
        assertThat(updates.getValue("x.y").source).isEqualTo(ManageUpdates.Source.PLAY)
    }

    @Test
    fun paidPlayAppIgnored() {
        val updates = ManageUpdates.pick(listOf(app("x.y", "1", 1)), null, null) {
            listOf(PlayVersion("x.y", 9, "2", isFree = false))
        }
        assertThat(updates).isEmpty()
    }

    @Test
    fun upToDateAppHasNoUpdate() {
        val catalog = StoreCatalog.Catalog(
            "https://a",
            listOf(catalogApp("com.waze", "5.0")),
            false
        )
        assertThat(
            ManageUpdates.pick(listOf(app("com.waze", "5.0")), null, catalog) {
                emptyList()
            }
        ).isEmpty()
    }

    @Test
    fun versionCompare() {
        assertThat(ManageUpdates.isVersionNewer("1.10.0", "1.9.9")).isTrue()
        assertThat(ManageUpdates.isVersionNewer("v1.0", "1.0")).isFalse()
        assertThat(ManageUpdates.isVersionNewer("", "1.0")).isFalse()
    }
}
