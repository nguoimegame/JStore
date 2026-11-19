/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManagedAppTest {

    @Test
    fun parsesRow() {
        val app = ManagedApp.fromMap(
            mapOf(
                "packageName" to "a.b",
                "label" to "AB",
                "versionCode" to 3L,
                "versionName" to "1",
                "hidden" to true,
                "protected" to false
            )
        )!!
        assertThat(app.packageName).isEqualTo("a.b")
        assertThat(app.hidden).isTrue()
        assertThat(app.icon).isNull()
    }

    @Test
    fun rowWithoutPackageIsDropped() {
        assertThat(ManagedApp.fromMap(mapOf("label" to "x"))).isNull()
    }

    @Test
    fun missingLabelFallsBackToPackage() {
        assertThat(ManagedApp.fromMap(mapOf("packageName" to "a.b"))!!.label).isEqualTo("a.b")
    }
}
