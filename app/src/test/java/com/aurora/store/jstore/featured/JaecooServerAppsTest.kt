/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.aurora.gplayapi.data.models.App
import com.aurora.store.jstore.ui.store.StoreCatalog
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JaecooServerAppsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val catalogue = """[
        {"name":"Jaecoo Music","packageName":"com.jaecoo.music","version":"1.2",
         "downloadUrl":"/files/music.apk"},
        {"name":"Spotify","packageName":"com.spotify.music"}
    ]"""

    private val featured = """[
        {"packageName":"com.waze"},
        {"name":"Radio","packageName":"com.jaecoo.radio","downloadUrl":"https://cdn/radio.apk"}
    ]"""

    /** The server answers both lists. */
    private fun live(url: String): String? = when (url) {
        "https://srv/api/apps.json" -> catalogue
        "https://srv/api/featured.json" -> featured
        else -> null
    }

    private fun dir() = File(folder.root, "jstore_store")

    private fun cache() = StoreCatalog.FileCatalogCache(dir())

    private fun build(
        fetch: (String) -> String? = { null },
        cache: StoreCatalog.CatalogCache = cache(),
        play: (List<String>) -> List<App> = { emptyList() }
    ) = JaecooServerApps.build(
        fetch = fetch,
        cache = cache,
        featuredCache = File(dir(), "featured.json"),
        language = "en",
        play = play,
        installed = { null }
    )

    @Test
    fun serverDownUsesCachedCatalogue() {
        cache().write("https://srv", catalogue)
        val requested = mutableListOf<String>()

        val snapshot = build(fetch = {
            requested += it
            null
        })

        // The catalogue came from the cache: featured.json is not tried on the network again.
        assertThat(requested).containsExactly("https://srv/api/apps.json")
        assertThat(snapshot.baseUrl).isEqualTo("https://srv")
        assertThat(snapshot.complete).isFalse()
        assertThat(snapshot.entries.map { it.app.packageName })
            .containsExactly("com.jaecoo.music", "com.spotify.music")
            .inOrder()
        assertThat(snapshot.apps.map { it.packageName }).containsExactly("com.jaecoo.music")
    }

    @Test
    fun noCacheNoServerIsEmpty() {
        val requested = mutableListOf<String>()

        val snapshot = build(fetch = {
            requested += it
            null
        })

        assertThat(requested).isEmpty()
        assertThat(snapshot).isEqualTo(JaecooServerApps.Snapshot.EMPTY)
        assertThat(snapshot.found).isFalse()
        assertThat(snapshot.apps).isEmpty()
        assertThat(snapshot.entries).isEmpty()
    }

    @Test
    fun playThrowsKeepsServerOnlyApps() {
        cache().write("https://srv", catalogue)
        val fetch: (String) -> String? = { url ->
            when (url) {
                "https://srv/api/apps.json" -> catalogue
                "https://srv/api/featured.json" -> featured
                else -> null
            }
        }

        val snapshot = build(fetch = fetch, play = { error("signed out") })

        assertThat(snapshot.found).isTrue()
        assertThat(snapshot.complete).isFalse()
        assertThat(snapshot.apps.map { it.packageName })
            .containsExactly("com.jaecoo.music", "com.jaecoo.radio")
            .inOrder()
        // Spotify is in apps.json without a downloadUrl: Play owns it.
        assertThat(snapshot.ownedPackages()).containsExactly("com.jaecoo.music", "com.jaecoo.radio")
        assertThat(snapshot.entry("com.waze")?.fromCatalogue).isFalse()
        assertThat(snapshot.app("com.spotify.music")).isNull()
    }

    @Test
    fun playAppsMergeWithServerEntries() {
        cache().write("https://srv", catalogue)
        val asked = mutableListOf<List<String>>()

        val snapshot = build(
            fetch = ::live,
            play = { pkgs ->
                asked += pkgs
                listOf(App(packageName = "com.waze", displayName = "Waze"))
            }
        )

        assertThat(asked).containsExactly(
            listOf("com.jaecoo.music", "com.spotify.music", "com.waze", "com.jaecoo.radio")
        )
        assertThat(snapshot.apps.map { it.packageName })
            .containsExactly("com.jaecoo.music", "com.waze", "com.jaecoo.radio")
            .inOrder()
        assertThat(snapshot.app("com.waze")?.displayName).isEqualTo("Waze")
    }

    @Test
    fun urlLessCatalogueAppIsSentToPlay() {
        cache().write("https://srv", catalogue)

        val snapshot = build(fetch = ::live)
        val forPlay = ServerUpdates.forPlay(
            listOf("com.jaecoo.music", "com.spotify.music", "com.waze", "com.jaecoo.radio"),
            snapshot.ownedPackages()
        )

        assertThat(forPlay).containsExactly("com.spotify.music", "com.waze").inOrder()
    }

    @Test
    fun isServerFileUrlMatchesResolvedUrl() {
        cache().write("https://srv", catalogue)

        val snapshot = build(fetch = ::live)

        assertThat(snapshot.isServerFileUrl("https://srv/files/music.apk")).isTrue()
        assertThat(snapshot.isServerFileUrl("https://cdn/radio.apk")).isTrue()
        assertThat(snapshot.isServerFileUrl("/files/music.apk")).isFalse()
        assertThat(snapshot.isServerFileUrl("https://play.googleapis.com/x.apk")).isFalse()
        assertThat(snapshot.isServerFileUrl("")).isFalse()
    }

    @Test
    fun liveCatalogueAndPlayIsComplete() {
        cache().write("https://srv", catalogue)

        val snapshot = build(
            fetch = ::live,
            play = { listOf(App(packageName = "com.waze", displayName = "Waze")) }
        )

        assertThat(snapshot.found).isTrue()
        assertThat(snapshot.complete).isTrue()
        assertThat(snapshot.apps.map { it.packageName })
            .containsExactly("com.jaecoo.music", "com.waze", "com.jaecoo.radio")
            .inOrder()
    }

    @Test
    fun cachedCatalogueUsesCachedFeatured() {
        cache().write("https://srv", catalogue)
        File(dir(), "featured.json").writeText(featured)

        val snapshot = build(fetch = { null })

        assertThat(snapshot.entry("com.waze")?.fromCatalogue).isFalse()
        assertThat(snapshot.apps.map { it.packageName })
            .containsExactly("com.jaecoo.music", "com.jaecoo.radio")
            .inOrder()
    }
}
