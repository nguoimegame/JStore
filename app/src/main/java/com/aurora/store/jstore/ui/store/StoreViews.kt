/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.view.View
import android.view.ViewGroup

internal object StoreViews {

    /** Turns off the default focus highlight on [root] and everything under it. */
    fun disableFocusHighlight(root: View) {
        root.defaultFocusHighlightEnabled = false
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) disableFocusHighlight(root.getChildAt(i))
        }
    }
}
