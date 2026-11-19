/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/** What Google Play knows about one package. */
data class PlayVersion(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val isFree: Boolean
)

/**
 * Google Play versions and downloads for packages JaecooApp manages. Shared by the update
 * service (for JaecooApp's own app manager) and the store screen drawn inside JaecooApp.
 *
 * Downloads bypass jstore's own download queue on purpose, so jstore never installs the
 * same update JaecooApp is about to install. See docs/jstore-contract.md.
 */
@Singleton
class PlayUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authProvider: AuthProvider,
    private val accountRepository: AccountRepository,
    private val httpClient: HttpClient
) {

    companion object {
        private const val TAG = "PlayUpdates"

        /** Packages being downloaded for JaecooApp; one download per package at a time. */
        private val active: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    /** A download that cannot happen, with a message the driver can read. */
    class UnsupportedAppException(message: String) : Exception(message)

    /** False when [packageName] is already being downloaded. Pair every true with [release]. */
    fun begin(packageName: String): Boolean = active.add(packageName)

    /** Empty when nothing is known, signed out or Play fails. Blocking. */
    fun findLatestVersions(packages: List<String>): List<PlayVersion> {
        if (packages.isEmpty()) return emptyList()
        return try {
            val authData = resolveAuthData(packages.first())
            AppDetailsHelper(authData).using(httpClient)
                .getAppByPackageName(packages)
                .map { PlayVersion(it.packageName, it.versionCode, it.versionName, it.isFree) }
        } catch (e: Exception) {
            JstoreLog.w(TAG, "Play update check failed", e)
            emptyList()
        }
    }

    /** Downloads the base and split APKs of [packageName]'s latest release. */
    fun fetch(packageName: String, onProgress: (Int) -> Unit): List<File> {
        val authData = resolveAuthData(packageName)
        val app = AppDetailsHelper(authData).using(httpClient).getAppByPackageName(packageName)
        if (!app.isFree) {
            throw UnsupportedAppException(
                context.getString(R.string.jstore_paid_unsupported)
            )
        }
        if (app.safeDependentLibraries().isNotEmpty()) {
            throw UnsupportedAppException(
                context.getString(R.string.jstore_shared_libs_unsupported)
            )
        }

        val certificateHash = CertUtil.getEncodedCertificateHashes(
            context,
            packageName
        ).lastOrNull()
        val purchaseHelper = PurchaseHelper(authData).using(httpClient)
        val playFiles = if (certificateHash != null) {
            purchaseHelper.purchase(packageName, app.versionCode, app.offerType, certificateHash)
        } else {
            purchaseHelper.purchase(packageName, app.versionCode, app.offerType)
        }
        if (playFiles.any { it.type == PlayFile.Type.OBB || it.type == PlayFile.Type.PATCH }) {
            throw UnsupportedAppException(context.getString(R.string.jstore_obb_unsupported))
        }
        val apks = playFiles.filter {
            it.type == PlayFile.Type.BASE ||
                it.type == PlayFile.Type.SPLIT
        }
        if (apks.isEmpty()) throw IOException(context.getString(R.string.purchase_no_file))

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
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            if (apk.sha256.isNotBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(apk.sha256, ignoreCase = true)) {
                    throw IOException(context.getString(R.string.verification_failed))
                }
            }
            target
        }
    }

    /** The account this package is bound to (or the default one), with a fresh session. */
    private fun resolveAuthData(packageName: String): AuthData = runBlocking {
        val accountId = accountRepository.resolveAccountId(packageName)
        if (accountRepository.getById(accountId) == null) {
            throw UnsupportedAppException(context.getString(R.string.jstore_sign_in_required))
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

    fun share(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileProvider",
        file
    ).also {
        context.grantUriPermission(
            JaecooConnection.JAECOO_PACKAGE,
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    fun release(packageName: String) {
        downloadDir(packageName).listFiles()?.forEach { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileProvider",
                file
            )
            context.revokeUriPermission(
                JaecooConnection.JAECOO_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        downloadDir(packageName).deleteRecursively()
        active.remove(packageName)
    }

    fun downloadDir(packageName: String) =
        File(context.cacheDir, "jaecoo/${File(packageName).name}")

    fun errorMessage(name: String, error: Exception): String = when (error) {
        is UnsupportedAppException -> error.message.orEmpty()
        else -> context.getString(
            R.string.jstore_download_failed,
            name,
            error.message ?: error.javaClass.simpleName
        )
    }
}
