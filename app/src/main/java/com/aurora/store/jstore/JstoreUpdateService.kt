/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import com.aurora.store.R
import com.aurora.store.util.PackageUtil
import com.example.jstore.updates.IDownloadCallback
import com.example.jstore.updates.IJstoreUpdates
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Google Play updates for JaecooApp's app manager: which installed apps have a newer
 * release, and the APKs of one, which JaecooApp then installs itself. See
 * docs/jstore-contract.md. The work itself is in [PlayUpdates].
 */
@AndroidEntryPoint
class JstoreUpdateService : Service() {

    companion object {
        private const val TAG = "JstoreUpdateService"
        const val ACTION_BIND_UPDATES = "com.example.jstore.action.BIND_UPDATES"

        fun toBundle(packageName: String, versionCode: Long, versionName: String, isFree: Boolean) =
            Bundle().apply {
                putString(JaecooConnection.KEY_PACKAGE_NAME, packageName)
                putLong(JaecooConnection.KEY_VERSION_CODE, versionCode)
                putString(JaecooConnection.KEY_VERSION_NAME, versionName)
                putBoolean(JaecooConnection.KEY_IS_FREE, isFree)
            }
    }

    @Inject
    lateinit var play: PlayUpdates

    private val binder = object : IJstoreUpdates.Stub() {

        override fun apiVersion(): Int = JaecooConnection.UPDATES_API_VERSION

        override fun latestVersions(packages: MutableList<String>?): MutableList<Bundle> {
            JaecooConnection.enforceCallerIsJaecoo(this@JstoreUpdateService)
            return play.findLatestVersions(packages.orEmpty())
                .map { toBundle(it.packageName, it.versionCode, it.versionName, it.isFree) }
                .toMutableList()
        }

        override fun download(packageName: String?, callback: IDownloadCallback?) {
            JaecooConnection.enforceCallerIsJaecoo(this@JstoreUpdateService)
            if (packageName.isNullOrEmpty() || callback == null) return
            startDownload(packageName, callback)
        }

        override fun release(packageName: String?) {
            JaecooConnection.enforceCallerIsJaecoo(this@JstoreUpdateService)
            if (packageName.isNullOrEmpty()) return
            play.release(packageName)
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_BIND_UPDATES) binder else null

    private fun startDownload(packageName: String, callback: IDownloadCallback) {
        val name = PackageUtil.getPackageLabel(this, packageName)?.ifBlank { null } ?: packageName
        if (!play.begin(packageName)) {
            callback.safely { onError(getString(R.string.jstore_download_busy, name)) }
            return
        }
        thread(name = "jaecoo-download") {
            try {
                val files = play.fetch(packageName) { progress ->
                    callback.safely { onProgress(progress) }
                }
                val uris = files.map { file -> play.share(file) }
                // JaecooApp calls release() once it has installed them.
                if (!callback.safely { onReady(uris) }) play.release(packageName)
            } catch (e: Exception) {
                JstoreLog.w(TAG, "Download of $packageName for JaecooApp failed", e)
                play.release(packageName)
                callback.safely { onError(play.errorMessage(name, e)) }
            }
        }
    }

    /** JaecooApp may be gone by the time a callback fires; false when it is. */
    private inline fun IDownloadCallback.safely(block: IDownloadCallback.() -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: RemoteException) {
            false
        }
}
