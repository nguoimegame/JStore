/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

/**
 * Decides whether [JaecooServerApps.ensureLoaded] loads again. A complete load is kept for the
 * session; an incomplete one (Play lookup failed, or the catalogue came from the cache) is tried
 * again, at most once per [retryGapMs] so repeated calls do not hammer a dead server. Without a
 * known server a load only reads the local cache, so it is always allowed. [now] is a monotonic
 * clock in milliseconds.
 */
class ServerLoadGate(private val now: () -> Long, private val retryGapMs: Long = RETRY_GAP_MS) {

    @Volatile
    private var lastAttemptAt: Long? = null

    fun shouldLoad(snapshot: JaecooServerApps.Snapshot): Boolean = when {
        snapshot.complete -> false
        !snapshot.found -> true
        else -> lastAttemptAt?.let { now() - it >= retryGapMs } ?: true
    }

    /** Records that a load starts now. */
    fun attempted() {
        lastAttemptAt = now()
    }

    companion object {
        const val RETRY_GAP_MS = 30_000L
    }
}
