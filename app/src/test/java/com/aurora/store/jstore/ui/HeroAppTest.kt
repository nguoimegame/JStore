/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.StreamCluster
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeroAppTest {

    private fun cluster(id: Int, vararg packages: String) =
        StreamCluster(id = id, clusterAppList = packages.map { App(packageName = it) })

    @Test
    fun noClustersGivesNull() {
        assertThat(heroApp(emptyList())).isNull()
    }

    @Test
    fun emptyFirstClusterGivesNull() {
        assertThat(heroApp(listOf(cluster(1), cluster(2, "c")))).isNull()
    }

    @Test
    fun firstAppOfFirstCluster() {
        val hero = heroApp(listOf(cluster(1, "a", "b"), cluster(2, "c")))
        assertThat(hero?.packageName).isEqualTo("a")
    }
}
