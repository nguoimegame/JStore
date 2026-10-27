/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.extensions.toast
import com.aurora.store.AuroraApp
import com.aurora.store.R
import com.aurora.store.data.installer.AppInstaller
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PackageUtil.isSharedLibraryInstalled
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Installs through JaecooApp, which holds Device Owner on the head unit: silent installs
 * and updates, with no confirmation for the driver. See docs/jstore-contract.md.
 *
 * Shared libraries go first, one package at a time, then the app itself.
 */
@Singleton
class JaecooInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerBase(context) {

    companion object {
        val installerInfo: InstallerInfo
            get() = InstallerInfo(
                id = 7,
                installer = Installer.JAECOO,
                installerPackageNames = listOf(JaecooConnection.JAECOO_PACKAGE),
                title = R.string.jstore_installer_title,
                subtitle = R.string.jstore_installer_subtitle,
                description = R.string.jstore_installer_desc
            )

        fun isAvailable(context: Context): Boolean = JaecooConnection.isAvailable(context)

        /**
         * Uninstalls through JaecooApp, without the system dialog. Falls back to that dialog
         * when JaecooApp cannot be reached.
         */
        fun uninstall(context: Context, packageName: String) {
            val appContext = context.applicationContext
            thread(name = "jaecoo-uninstall") {
                when (val result = JaecooConnection.uninstall(appContext, packageName)) {
                    is JaecooConnection.Result.Failed -> appContext.toast(
                        appContext.getString(R.string.jstore_uninstall_failed, result.message)
                    )

                    is JaecooConnection.Result.Unavailable ->
                        AppInstaller.uninstallWithSystemDialog(appContext, packageName)

                    else -> Log.i(TAG, "Uninstall of $packageName: $result")
                }
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun install(download: Download) {
        super.install(download)
        if (isAlreadyQueued(download.packageName)) {
            Log.i(TAG, "${download.packageName} already queued")
            return
        }
        AuroraApp.enqueuedInstalls.add(download.packageName)
        Log.i(TAG, "Received JaecooApp install request for ${download.packageName}")

        executor.execute {
            val librariesOk = download.sharedLibs
                // Shared library packages cannot be updated
                .filterNot { isSharedLibraryInstalled(context, it.packageName, it.versionCode) }
                .all { library ->
                    installPackage(download.packageName, download.versionCode, library.packageName)
                }
            if (librariesOk && installPackage(download.packageName, download.versionCode)) {
                onInstallationSuccess()
            }
            removeFromInstallQueue(download.packageName)
        }
    }

    /**
     * Hands one package's APKs to JaecooApp. True once it is installed or the system's
     * confirmation is showing; false after an error was posted.
     */
    private fun installPackage(
        packageName: String,
        versionCode: Long,
        sharedLibPackageName: String = ""
    ): Boolean {
        val target = sharedLibPackageName.ifBlank { packageName }
        val uris = getFiles(packageName, versionCode, sharedLibPackageName).map { file ->
            getUri(file).also { uri ->
                context.grantUriPermission(
                    JaecooConnection.JAECOO_PACKAGE,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        val result = JaecooConnection.install(context, target, uris)
        uris.forEach { uri ->
            context.revokeUriPermission(
                JaecooConnection.JAECOO_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        return when (result) {
            is JaecooConnection.Result.Installed -> true

            // The confirmation is up; the package receiver picks up the app's outcome, so
            // only a shared library goes on to the next package.
            is JaecooConnection.Result.Launched -> {
                Log.i(TAG, "$target waits for the driver to confirm")
                sharedLibPackageName.isNotBlank()
            }

            is JaecooConnection.Result.Failed -> {
                postError(
                    target,
                    context.getString(R.string.installer_status_failure),
                    result.message
                )
                false
            }

            is JaecooConnection.Result.Unavailable -> {
                postError(
                    target,
                    context.getString(R.string.installer_status_failure),
                    context.getString(R.string.jstore_jaecooapp_required)
                )
                false
            }

            is JaecooConnection.Result.Uninstalled -> false
        }
    }
}
