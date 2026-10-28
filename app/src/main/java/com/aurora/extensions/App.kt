/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.extensions

import com.aurora.Constants.PACKAGE_NAME_GMS
import com.aurora.gplayapi.data.models.App

fun App.requiresGMS(): Boolean {
    val deps: Any? = runCatching { dependencies }.getOrNull() ?: return false
    val pkgs: Any? =
        runCatching {
            (deps as? com.aurora.gplayapi.data.models.details.Dependencies)?.dependentPackages
        }.getOrNull()
            ?: return false
    return (pkgs as? List<*>)?.contains(PACKAGE_NAME_GMS) == true
}
