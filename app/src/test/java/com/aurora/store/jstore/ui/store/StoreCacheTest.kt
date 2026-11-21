/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoreCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writeAtomicallyCreatesDirectoryAndLeavesNoTempFile() {
        val dir = File(folder.root, "nested")
        val target = File(dir, "apps.json")
        StoreCache.writeAtomically(target, "body")
        assertThat(target.readText()).isEqualTo("body")
        assertThat(dir.listFiles()!!.map { it.name }).containsExactly("apps.json")
    }

    @Test
    fun writeAtomicallyReplacesExistingFile() {
        val target = folder.newFile("featured.json").apply { writeText("old body, longer") }
        StoreCache.writeAtomically(target, "new")
        assertThat(target.readText()).isEqualTo("new")
        assertThat(folder.root.listFiles()!!.map { it.name }).containsExactly("featured.json")
    }

    @Test
    fun fileCacheRoundTripsAndLeavesNoTempFiles() {
        val dir = File(folder.root, StoreCache.DIR)
        val cache = StoreCatalog.FileCatalogCache(dir)
        cache.write("https://a", "[1]")
        cache.write("https://b", "[2]")
        assertThat(cache.read()).isEqualTo("https://b" to "[2]")
        assertThat(dir.listFiles()!!.map { it.name }).containsExactly("apps.json", "server.txt")
    }

    @Test
    fun fileCacheWithOnlyOneHalfReadsAsNothing() {
        val dir = folder.newFolder(StoreCache.DIR)
        File(dir, "apps.json").writeText("[1]")
        assertThat(StoreCatalog.FileCatalogCache(dir).read()).isNull()
    }

    @Test
    fun sharedPathsPointAtTheSameFiles() {
        val cacheDir = folder.root
        assertThat(StoreCache.dir(cacheDir)).isEqualTo(File(cacheDir, "jstore_store"))
        assertThat(StoreCache.featuredFile(cacheDir))
            .isEqualTo(File(File(cacheDir, "jstore_store"), "featured.json"))
    }
}
