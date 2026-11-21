/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeaturedListTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = """[{"id":"waze","name":"Waze","packageName":"com.waze"}]"""

    private fun cacheFile() = File(folder.root, "jstore_store/featured.json")

    @Test
    fun fetchesFeaturedPathAndCaches() {
        val requested = mutableListOf<String>()
        val list = FeaturedList(
            fetch = {
                requested += it
                json
            },
            cacheFile = cacheFile()
        )

        val apps = list.load("https://a", "en")

        assertThat(requested).containsExactly("https://a/api/featured.json")
        assertThat(apps.map { it.packageName }).containsExactly("com.waze")
        assertThat(cacheFile().readText()).isEqualTo(json)
    }

    @Test
    fun brokenJsonFallsBackToCache() {
        cacheFile().apply { parentFile!!.mkdirs() }.writeText(json)
        val list = FeaturedList(fetch = { "not json" }, cacheFile = cacheFile())

        assertThat(list.load("https://a", "en").map { it.packageName }).containsExactly("com.waze")
        assertThat(cacheFile().readText()).isEqualTo(json)
    }

    @Test
    fun unreachableServerFallsBackToCache() {
        cacheFile().apply { parentFile!!.mkdirs() }.writeText(json)
        val list = FeaturedList(
            fetch = { throw IllegalArgumentException("bad url") },
            cacheFile = cacheFile()
        )

        assertThat(list.load("nope", "en")).hasSize(1)
    }

    @Test
    fun missingEverythingIsEmpty() {
        val list = FeaturedList(fetch = { null }, cacheFile = cacheFile())

        assertThat(list.load("https://a", "en")).isEmpty()
    }

    @Test
    fun cacheHoldsLastFullBodyAndNoTempFiles() {
        val newer = """[{"id":"maps","name":"Maps","packageName":"com.maps"}]"""
        var body = json
        val list = FeaturedList(fetch = { body }, cacheFile = cacheFile())

        list.load("https://a", "en")
        body = newer
        list.load("https://a", "en")

        assertThat(cacheFile().readText()).isEqualTo(newer)
        assertThat(cacheFile().parentFile!!.list()!!.toList()).containsExactly("featured.json")
    }

    @Test
    fun cachedReadsOnlyTheCache() {
        cacheFile().apply { parentFile!!.mkdirs() }.writeText(json)

        val apps = FeaturedList(fetch = { error("no network") }, cacheFile = cacheFile())
            .cached("en")

        assertThat(apps.map { it.packageName }).containsExactly("com.waze")
    }
}
