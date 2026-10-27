/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.jaecooapp.installer.IInstallCallback
import com.example.jaecooapp.installer.IJaecooInstaller
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * JaecooApp, which holds Device Owner on the head unit and installs for jstore.
 * The names here are jstore's copy of docs/jstore-contract.md; JaecooApp keeps its own.
 *
 * [install] and [uninstall] block until JaecooApp answers, so call them off the main thread.
 */
object JaecooConnection {

    private const val TAG = "JaecooConnection"

    const val API_VERSION = 1

    const val JAECOO_PACKAGE = "com.example.jaecooapp"
    private const val INSTALLER_SERVICE = "com.example.jaecooapp.JaecooInstallerService"
    private const val ACTION_BIND_INSTALLER = "com.example.jaecooapp.action.BIND_INSTALLER"

    /** IInstallCallback.onResult statuses. */
    const val STATUS_INSTALLED = 0
    const val STATUS_LAUNCHED = 1
    const val STATUS_FAILED = 2
    const val STATUS_UNINSTALLED = 3

    /** Keys of the bundles IJstoreUpdates.latestVersions returns. */
    const val KEY_PACKAGE_NAME = "packageName"
    const val KEY_VERSION_CODE = "versionCode"
    const val KEY_VERSION_NAME = "versionName"
    const val KEY_IS_FREE = "isFree"

    private const val BIND_TIMEOUT_SECONDS = 10L

    /** Long enough for a large app to be copied and installed. */
    private const val RESULT_TIMEOUT_MINUTES = 5L

    sealed interface Result {
        data object Installed : Result

        /** JaecooApp is not Device Owner and opened the system confirmation instead. */
        data object Launched : Result

        data object Uninstalled : Result

        data class Failed(val message: String) : Result

        /** JaecooApp is missing, too old, or signed with another key. */
        data object Unavailable : Result
    }

    /** Whether a JaecooApp new enough to install for jstore is on the head unit. */
    fun isAvailable(context: Context): Boolean =
        context.packageManager.resolveService(bindIntent(), 0) != null

    fun install(context: Context, packageName: String, apks: List<Uri>): Result =
        call(context) { installer, callback -> installer.install(packageName, apks, callback) }

    fun uninstall(context: Context, packageName: String): Result =
        call(context) { installer, callback -> installer.uninstall(packageName, callback) }

    fun toResult(status: Int, message: String): Result = when (status) {
        STATUS_INSTALLED -> Result.Installed
        STATUS_LAUNCHED -> Result.Launched
        STATUS_UNINSTALLED -> Result.Uninstalled
        else -> Result.Failed(message)
    }

    /**
     * Throws unless the app on the other end of the current binder call is JaecooApp. The
     * signature permission alone is not enough: whichever app is installed first defines a
     * permission both of them declare, so the package is checked as well.
     */
    fun enforceCallerIsJaecoo(context: Context) {
        val uid = Binder.getCallingUid()
        if (!isPackageUid(context.packageManager, uid, JAECOO_PACKAGE)) {
            throw SecurityException("uid $uid is not $JAECOO_PACKAGE")
        }
    }

    fun isPackageUid(packageManager: PackageManager, uid: Int, packageName: String): Boolean =
        packageManager.getPackagesForUid(uid)?.contains(packageName) == true

    private fun call(
        context: Context,
        request: (IJaecooInstaller, IInstallCallback) -> Unit
    ): Result {
        val connection = Connection()
        val appContext = context.applicationContext
        return try {
            val installer = connection.open(appContext)
            if (installer == null || installer.apiVersion() < API_VERSION) {
                Result.Unavailable
            } else {
                val done = CountDownLatch(1)
                val result = AtomicReference<Result>(Result.Failed("JaecooApp did not answer"))
                request(
                    installer,
                    object : IInstallCallback.Stub() {
                        override fun onResult(status: Int, message: String?) {
                            result.set(toResult(status, message.orEmpty()))
                            done.countDown()
                        }
                    }
                )
                done.await(RESULT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                result.get()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Installer call failed", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            connection.close(appContext)
        }
    }

    private fun bindIntent() = Intent(ACTION_BIND_INSTALLER)
        .setClassName(JAECOO_PACKAGE, INSTALLER_SERVICE)

    /** One bind to JaecooApp's installer service, waited for on the calling thread. */
    private class Connection : ServiceConnection {
        private val bound = AtomicReference<IJaecooInstaller?>()
        private val connected = CountDownLatch(1)

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            bound.set(IJaecooInstaller.Stub.asInterface(service))
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bound.set(null)
        }

        override fun onNullBinding(name: ComponentName) {
            connected.countDown()
        }

        /** The installer, or null when JaecooApp cannot be bound. */
        fun open(context: Context): IJaecooInstaller? {
            val didBind = try {
                context.bindService(bindIntent(), this, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                // Our copy of the permission is not granted: the keys differ.
                Log.w(TAG, "JaecooApp refused the bind", e)
                false
            }
            return if (didBind && connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                bound.get()
            } else {
                null
            }
        }

        /** A failed bind still has to be released, and may never have been registered. */
        fun close(context: Context) {
            try {
                context.unbindService(this)
            } catch (_: IllegalArgumentException) {
                // Not registered: nothing to release.
            }
        }
    }
}
