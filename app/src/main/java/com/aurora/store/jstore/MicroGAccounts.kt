/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.aurora.store.util.CertUtil.GOOGLE_ACCOUNT_TYPE

/**
 * The microG build jstore signs in through. jstore requires microG: there is no anonymous
 * or WebView sign-in.
 *
 * Upstream only accepts microG installed as `com.google.android.gms`. Head units often run a
 * repackaged build instead, such as ReVanced's `app.revanced.android.gms` ("MicroG RE"), which
 * lives under its own package and account type so it can sit next to Google's Play Services.
 * microG is therefore found through the account authenticator it registers, and the account
 * type comes from there.
 */
object MicroGAccounts {

    private const val FAKE_SIGNATURE_PERMISSION = "android.permission.FAKE_PACKAGE_SIGNATURE"

    /** Oldest microG build whose Play token handling has been proven with this flow. */
    private const val MIN_MICROG_VERSION = 240913402L

    data class MicroG(val packageName: String, val accountType: String)

    /**
     * The usable microG build, or null. Google's own Play Services also registers
     * `com.google`, so an authenticator only counts when the package behind it is microG;
     * stock microG is preferred when several builds are installed.
     */
    fun find(context: Context): MicroG? {
        val pm = context.packageManager
        return AccountManager.get(context).authenticatorTypes
            .filter { pm.isUsableMicroG(it.packageName) }
            .sortedByDescending { it.type == GOOGLE_ACCOUNT_TYPE }
            .firstOrNull()
            ?.let { MicroG(it.packageName, it.type) }
    }

    /** Account type to sign in under: microG's, or upstream's default when none is found. */
    fun accountType(context: Context): String = find(context)?.accountType ?: GOOGLE_ACCOUNT_TYPE

    private fun PackageManager.isUsableMicroG(packageName: String): Boolean {
        val info = try {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return info.isMicroG() && PackageInfoCompat.getLongVersionCode(info) >= MIN_MICROG_VERSION
    }

    private fun PackageInfo.isMicroG(): Boolean =
        requestedPermissions?.contains(FAKE_SIGNATURE_PERMISSION) == true
}
