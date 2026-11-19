/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.Context
import android.content.Intent
import android.view.View
import com.aurora.store.R
import com.aurora.store.data.network.HttpClient
import com.aurora.store.jstore.PlayUpdates
import com.aurora.store.jstore.embed.StoreHostConfig

/** Builds the store screen that [com.aurora.store.jstore.embed.StoreUiService] puts on its surface. */
object StoreScreenFactory {

    /** Theme the views are inflated with. */
    val THEME: Int = R.style.jstore_store_Theme

    /** What the screen asks of JaecooApp; each call crosses the process boundary. */
    interface Host {
        fun startActivity(intent: Intent)
        fun requestPhoneUpload()
        fun requestJstoreUpdate()
        fun toast(message: String)
    }

    interface Screen {
        val view: View
        fun select(section: String)
        fun currentSection(): String
        fun dispose()
    }

    /** What the screen needs from jstore's own services. */
    class Deps(val httpClient: HttpClient, val play: PlayUpdates)

    fun create(
        context: Context,
        config: StoreHostConfig,
        section: String,
        host: Host,
        deps: Deps
    ): Screen = StoreScreen(context, config, section, host, deps)
}
