/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import com.aurora.store.data.network.HttpClient
import com.aurora.store.jstore.JstoreLog
import java.io.IOException

/** Plain-text GETs for the Jaecoo server (and GitHub), shared by the Kho tab and its helpers. */
object StoreHttp {

    /** The body of [url], or null when it cannot be read (logged under [tag]). Blocking. */
    fun fetchText(
        httpClient: HttpClient,
        tag: String,
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String? = try {
        httpClient.call(url, headers).use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    } catch (e: IOException) {
        JstoreLog.w(tag, "Could not read $url", e)
        null
    } catch (e: IllegalArgumentException) {
        // OkHttp refuses a URL it cannot parse; an uncaught throw here would end jstore's process.
        JstoreLog.w(tag, "Not a usable address: $url", e)
        null
    }
}
