/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.content.Context
import com.aurora.store.R
import com.aurora.store.data.model.Link

/**
 * The About screen's links for jstore: its own source first, then the Aurora Store project
 * it is based on. Upstream's support, store and donation links are for Aurora Store itself,
 * not for this fork, so they are left out.
 */
object JstoreAbout {

    const val SOURCE_URL = "https://github.com/nguoimegame/JStore"
    private const val UPSTREAM_URL = "https://gitlab.com/AuroraOSS/AuroraStore"

    /** Upstream's link ids: 0 is "About", which opens the about dialog. */
    private const val UPSTREAM_ABOUT_ID = 0

    fun links(context: Context, upstream: List<Link>): List<Link> = listOfNotNull(
        upstream.firstOrNull { it.id == UPSTREAM_ABOUT_ID },
        Link(
            id = 100,
            title = context.getString(R.string.jstore_about_source_title),
            subtitle = context.getString(R.string.jstore_about_source_subtitle),
            url = SOURCE_URL,
            icon = R.drawable.ic_code
        ),
        Link(
            id = 101,
            title = context.getString(R.string.jstore_about_upstream_title),
            subtitle = context.getString(R.string.jstore_about_upstream_subtitle),
            url = UPSTREAM_URL,
            icon = R.drawable.ic_gitlab
        )
    )
}
