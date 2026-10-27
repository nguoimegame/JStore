/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

/**
 * Upstream features jstore does not offer on the head unit. They are switched off where they
 * are entered rather than deleted, so merging upstream stays small.
 */
object JstoreFeatures {
    /** Play ratings, featured reviews and writing a review. */
    const val REVIEWS = false

    /** Joining or leaving an app's beta program. */
    const val BETA_PROGRAMS = false

    /** Exodus Privacy tracker reports (needs an API key and an outside service). */
    const val EXODUS = false

    /** Plexus compatibility scores for microG. */
    const val PLEXUS = false

    /** Downloading an older version by its version code. */
    const val MANUAL_DOWNLOAD = false

    /** Aurora's own self-update from its server; JaecooApp updates jstore instead. */
    const val SELF_UPDATE = false
}
