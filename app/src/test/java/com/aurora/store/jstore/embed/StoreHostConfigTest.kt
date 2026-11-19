/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.embed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StoreHostConfigTest {

    @Test
    fun parsesAll() {
        val config = StoreHostConfig.fromMap(
            mapOf(
                "language" to "vi",
                "night_mode" to true,
                "screen_height_dp" to 1280,
                "server_urls" to arrayListOf("https://a", "https://b"),
                "initial_section" to "manage"
            )
        )
        assertThat(config.language).isEqualTo("vi")
        assertThat(config.nightMode).isTrue()
        assertThat(config.serverUrls).containsExactly("https://a", "https://b").inOrder()
        assertThat(config.initialSection).isEqualTo("manage")
        assertThat(config.isTall()).isTrue()
    }

    @Test
    fun emptyConfigIsShortWithDefaultSection() {
        val config = StoreHostConfig.fromMap(emptyMap())
        assertThat(config.isTall()).isFalse()
        assertThat(config.initialSection).isEqualTo("jaecoo")
        assertThat(config.serverUrls).isEmpty()
    }

    @Test
    fun unknownSectionFallsBackToJaecoo() {
        assertThat(
            StoreHostConfig.fromMap(mapOf("initial_section" to "x")).initialSection
        ).isEqualTo("jaecoo")
    }

    @Test
    fun heightExactlyAtThresholdIsTall() {
        assertThat(StoreHostConfig.fromMap(mapOf("screen_height_dp" to 1024)).isTall()).isTrue()
        assertThat(StoreHostConfig.fromMap(mapOf("screen_height_dp" to 1023)).isTall()).isFalse()
    }

    @Test
    fun crossingTallThresholdReinflates() {
        val low = StoreHostConfig.fromMap(mapOf("screen_height_dp" to 720))
        assertThat(
            StoreHostConfig.fromMap(mapOf("screen_height_dp" to 1280)).needsReinflate(low)
        ).isTrue()
        assertThat(
            StoreHostConfig.fromMap(mapOf("screen_height_dp" to 800)).needsReinflate(low)
        ).isFalse()
    }

    @Test
    fun languageOrNightChangeReinflates() {
        val base = StoreHostConfig.fromMap(mapOf("language" to "vi", "night_mode" to false))
        assertThat(
            StoreHostConfig.fromMap(
                mapOf("language" to "en", "night_mode" to false)
            ).needsReinflate(base)
        ).isTrue()
        assertThat(
            StoreHostConfig.fromMap(
                mapOf("language" to "vi", "night_mode" to true)
            ).needsReinflate(base)
        ).isTrue()
        assertThat(base.needsReinflate(base)).isFalse()
    }

    @Test
    fun onlyJaecooAppMayBind() {
        assertThat(StoreUiCallers.isAllowedCaller(arrayOf("com.example.jaecooapp"))).isTrue()
        assertThat(StoreUiCallers.isAllowedCaller(arrayOf("com.evil"))).isFalse()
        assertThat(StoreUiCallers.isAllowedCaller(null)).isFalse()
    }
}
