/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store

import androidx.lifecycle.ViewModel
import com.aurora.store.data.helper.UpdateHelper
import com.aurora.store.data.providers.AuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val updateHelper: UpdateHelper,
    // jstore
    private val authProvider: AuthProvider
) : ViewModel() {
    /** jstore: the signed-in account, shown under the main screen's title. */
    val accountEmail: String
        get() = authProvider.authData?.email.orEmpty()
}
