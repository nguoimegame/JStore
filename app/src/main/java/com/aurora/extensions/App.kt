/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.extensions

import com.aurora.Constants.PACKAGE_NAME_GMS
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.details.Dependencies

fun App.requiresGMS(): Boolean = safeDependentPackages().contains(PACKAGE_NAME_GMS)

fun App.safeDependentPackages(): List<String> = try {
    val deps = (dependencies as Any?) as? Dependencies
    (deps?.dependentPackages as? List<*>)?.filterIsInstance<String>().orEmpty()
} catch (_: Throwable) {
    emptyList()
}

fun App.safeDependentLibraries(): List<App> = try {
    val deps = (dependencies as Any?) as? Dependencies
    (deps?.dependentLibraries as? List<*>)?.filterIsInstance<App>().orEmpty()
} catch (_: Throwable) {
    emptyList()
}
