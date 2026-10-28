/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.core.content.FileProvider
import com.aurora.extensions.safeDependentLibraries
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.data.AccountRepository
import com.aurora.store.data.network.HttpClient
import com.aurora.store.data.providers.AuthProvider
import com.aurora.store.util.CertUtil
import com.aurora.store.util.PackageUtil
import com.example.jstore.updates.IDownloadCallback
import com.example.jstore.updates.IJstoreUpdates
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/**
 * Google Play updates for JaecooApp's app manager: which installed apps have a newer
 * release, and the APKs of one, which JaecooApp then installs itself. See
 * docs/jstore-contract.md.
 *
 * Downloads bypass jstore's own download queue on purpose, so jstore never installs the
 * same update JaecooApp is about to install.
 */
@AndroidEntryPoint
class JstoreUpdateService : Service() {

    companion object {
        private const val TAG = "JstoreUpdateService"
        const val ACTION_BIND_UPDATES = "com.example.jstore.action.BIND_UPDATES"

        /** Packages being downloaded for JaecooApp; one download per package at a time. */
        private val active: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        fun toBundle(packageName: String, versionCode: Long, versionName: String, isFree: Boolean) =
            Bundle().apply {
                putString(JaecooConnection.KEY_PACKAGE_NAME, packageName)
                putLong(JaecooConnection.KEY_VERSION_CODE, versionCode)
                putString(JaecooConnection.KEY_VERSION_NAME, versionName)
                putBoolean(JaecooConnection.KEY_IS_FREE, isFree)
            }
    }

    @Inject
    lateinit var authProvider: AuthProvider

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var httpClient: HttpClient

    private val binder = object : IJstoreUpdates.Stub() {

        override fun apiVersion(): Int = JaecooConnection.API_VERSION

        override fun latestVersions(packages: MutableList<String>?): MutableList<Bundle> {
            JaecooConnection.enforceCallerIsJaecoo(this@JstoreUpdateService)
            return findLatestVersions(packages.orEmpty()).toMutableList()
        }

        override fun download(packageName: String?, callback: IDownloadCallback?) {
            JaecooConnection.enforceCallerIsJaecoo(this@JstoreUpdateService)
            if (packageName.isNullOrEmpty() || callback == null) return
            startDownload(packageName, callback)
        }

        override fun release(packageName: String?) {
            JaecooConnection.enforceCallerIsJaecoo(this@JstoreUpdateService)
            if (packageName.isNullOrEmpty()) return
            releaseDownload(packageName)
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_BIND_UPDATES) binder else null

    private fun findLatestVersions(packages: List<String>): List<Bundle> {
        if (packages.isEmpty()) return emptyList()
        return try {
            val authData = resolveAuthData(packages.first())
            AppDetailsHelper(authData).using(httpClient)
                .getAppByPackageName(packages)
                .map { toBundle(it.packageName, it.versionCode, it.versionName, it.isFree) }
        } catch (e: Exception) {
            Log.w(TAG, "Play update check failed", e)
            emptyList()
        }
    }

    private fun startDownload(packageName: String, callback: IDownloadCallback) {
        val name = PackageUtil.getPackageLabel(this, packageName)?.ifBlank { null } ?: packageName
        if (!active.add(packageName)) {
            callback.safely { onError(getString(R.string.jstore_download_busy, name)) }
            return
        }
        thread(name = "jaecoo-download") {
            try {
                val files = fetch(packageName, callback)
                val uris = files.map { file -> share(file) }
                // JaecooApp calls release() once it has installed them.
                if (!callback.safely { onReady(uris) }) releaseDownload(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Download of $packageName for JaecooApp failed", e)
                releaseDownload(packageName)
                callback.safely { onError(errorMessage(name, e)) }
            }
        }
    }

    /** Downloads the base and split APKs of [packageName]'s latest release. */
    private fun fetch(packageName: String, callback: IDownloadCallback): List<File> {
        val authData = resolveAuthData(packageName)
        val app = AppDetailsHelper(authData).using(httpClient).getAppByPackageName(packageName)
        if (!app.isFree) throw UnsupportedAppException(getString(R.string.jstore_paid_unsupported))
        if (app.safeDependentLibraries().isNotEmpty()) {
            throw UnsupportedAppException(getString(R.string.jstore_shared_libs_unsupported))
        }

        val certificateHash = CertUtil.getEncodedCertificateHashes(this, packageName).lastOrNull()
        val purchaseHelper = PurchaseHelper(authData).using(httpClient)
        val playFiles = if (certificateHash != null) {
            purchaseHelper.purchase(packageName, app.versionCode, app.offerType, certificateHash)
        } else {
            purchaseHelper.purchase(packageName, app.versionCode, app.offerType)
        }
        if (playFiles.any { it.type == PlayFile.Type.OBB || it.type == PlayFile.Type.PATCH }) {
            throw UnsupportedAppException(getString(R.string.jstore_obb_unsupported))
        }
        val apks = playFiles.filter {
            it.type == PlayFile.Type.BASE ||
                it.type == PlayFile.Type.SPLIT
        }
        if (apks.isEmpty()) throw IOException(getString(R.string.purchase_no_file))

        val dir = downloadDir(packageName).apply {
            deleteRecursively()
            mkdirs()
        }
        val totalBytes = apks.sumOf { it.size }.coerceAtLeast(1L)
        var doneBytes = 0L
        var lastProgress = -1
        return apks.map { apk ->
            val target = File(dir, apk.name)
            val digest = MessageDigest.getInstance("SHA-256")
            httpClient.call(apk.url).use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            doneBytes += count
                            val progress = ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                callback.safely { onProgress(progress) }
                            }
                        }
                    }
                }
            }
            if (apk.sha256.isNotBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(apk.sha256, ignoreCase = true)) {
                    throw IOException(getString(R.string.verification_failed))
                }
            }
            target
        }
    }

    /** The account this package is bound to (or the default one), with a fresh session. */
    private fun resolveAuthData(packageName: String): AuthData = runBlocking {
        val accountId = accountRepository.resolveAccountId(packageName)
        if (accountRepository.getById(accountId) == null) {
            throw UnsupportedAppException(getString(R.string.jstore_sign_in_required))
        }
        val stored = authProvider.getAuthData(accountId)
        if (stored != null &&
            authProvider.matchesActiveSpoof(stored) &&
            AuthHelper.using(httpClient).isValid(stored)
        ) {
            stored
        } else {
            authProvider.refresh(accountId).getOrThrow()
        }
    }

    private fun share(file: File): Uri =
        FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileProvider", file).also {
            grantUriPermission(
                JaecooConnection.JAECOO_PACKAGE,
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    private fun releaseDownload(packageName: String) {
        downloadDir(packageName).listFiles()?.forEach { file ->
            val uri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileProvider",
                file
            )
            revokeUriPermission(
                JaecooConnection.JAECOO_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        downloadDir(packageName).deleteRecursively()
        active.remove(packageName)
    }

    private fun downloadDir(packageName: String) =
        File(cacheDir, "jaecoo/${File(packageName).name}")

    private fun errorMessage(name: String, error: Exception): String = when (error) {
        is UnsupportedAppException -> error.message.orEmpty()
        else -> getString(
            R.string.jstore_download_failed,
            name,
            error.message ?: error.javaClass.simpleName
        )
    }

    private class UnsupportedAppException(message: String) : Exception(message)

    /** JaecooApp may be gone by the time a callback fires; false when it is. */
    private inline fun IDownloadCallback.safely(block: IDownloadCallback.() -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: RemoteException) {
            false
        }
}
