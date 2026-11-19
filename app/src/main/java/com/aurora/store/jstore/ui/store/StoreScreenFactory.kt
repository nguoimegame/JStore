/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.aurora.store.R
import com.aurora.store.jstore.embed.StoreHostConfig

/** Builds the store screen that [com.aurora.store.jstore.embed.StoreUiService] puts on its surface. */
object StoreScreenFactory {

    /** Theme the views are inflated with. */
    val THEME: Int = R.style.AppTheme

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

    fun create(context: Context, config: StoreHostConfig, section: String, host: Host): Screen {
        val text = TextView(context).apply { text = "jstore store: $section" }
        return object : Screen {
            override val view: View = text
            override fun select(section: String) {
                text.text = "jstore store: $section"
            }
            override fun currentSection(): String = text.text.toString().substringAfter(": ")
            override fun dispose() = Unit
        }
    }
}
