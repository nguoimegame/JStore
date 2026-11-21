/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.App
import com.aurora.store.jstore.featured.JaecooServerApps
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Feeds the "Apps for Jaecoo" cluster on Apps > For you. */
@HiltViewModel
class JaecooFeaturedViewModel @Inject constructor(
    private val repo: JaecooServerApps
) : ViewModel() {

    val apps: StateFlow<List<App>> = repo.apps

    init {
        viewModelScope.launch { repo.ensureLoaded() }
    }
}
