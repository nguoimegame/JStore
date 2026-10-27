/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.aurora.store.data.model.Installer

/**
 * Resolves which installer should be used given the user's saved preference ordinal (null if unset)
 * and whether JaecooApp is available on this device.
 * When unset, defaults to [Installer.JAECOO] if JaecooApp is available, otherwise [Installer.SESSION].
 */
fun resolveInstaller(savedOrdinal: Int?, jaecooAvailable: Boolean): Installer {
    if (savedOrdinal != null && savedOrdinal in Installer.entries.indices) {
        return Installer.entries[savedOrdinal]
    }
    return if (jaecooAvailable) Installer.JAECOO else Installer.SESSION
}

/**
 * Whether installing needs Android's "install unknown apps" permission for jstore. Only the
 * system installers need it; JaecooApp installs with its Device Owner rights, so while it is the
 * chosen installer and is on the head unit, asking for the permission only blocks the install.
 * When JaecooApp is chosen but missing, jstore falls back to the system installer, which does
 * need the permission.
 */
fun needsUnknownAppsPermission(selected: Installer, jaecooAvailable: Boolean): Boolean =
    !(selected == Installer.JAECOO && jaecooAvailable)
