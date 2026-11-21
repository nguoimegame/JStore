/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerLoadGateTest {

    private var now = 1_000L

    private val gate = ServerLoadGate(now = { now }, retryGapMs = 30_000L)

    private val complete = JaecooServerApps.Snapshot(emptyList(), emptyList(), "https://srv", true)

    private val incomplete =
        JaecooServerApps.Snapshot(emptyList(), emptyList(), "https://srv", false)

    @Test
    fun completeSnapshotIsNotReloaded() {
        assertThat(gate.shouldLoad(complete)).isFalse()
        gate.attempted()
        now += 60_000L
        assertThat(gate.shouldLoad(complete)).isFalse()
    }

    @Test
    fun incompleteSnapshotIsRetriedAfterTheGap() {
        assertThat(gate.shouldLoad(incomplete)).isTrue()
        gate.attempted()
        now += 30_000L
        assertThat(gate.shouldLoad(incomplete)).isTrue()
    }

    @Test
    fun retryGapIsHonoured() {
        gate.attempted()
        now += 29_999L
        assertThat(gate.shouldLoad(incomplete)).isFalse()
    }

    @Test
    fun noCacheSnapshotIsAlwaysRetried() {
        // Without a known server a load only reads the local cache, so there is nothing to spare.
        gate.attempted()
        assertThat(gate.shouldLoad(JaecooServerApps.Snapshot.EMPTY)).isTrue()
    }
}
