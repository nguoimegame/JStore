/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.featured

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.store.data.network.HttpClient
import com.aurora.store.jstore.JstoreLog
import com.aurora.store.jstore.ui.store.StoreCatalog
import com.aurora.store.util.CertUtil
import com.aurora.store.util.PackageUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The Jaecoo server's apps (`apps.json` + `featured.json`) merged with Google Play, held for the
 * whole process. The server is the one the Kho tab last reached (its `jstore_store` cache); before
 * that tab has ever loaded there is no server, so nothing is listed and nothing is server-owned.
 */
@Singleton
class JaecooServerApps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val appDetailsHelper: Provider<AppDetailsHelper>
) {

    /**
     * One load: every server entry, the apps they merged into, and the server that answered.
     * [complete] is true when the catalogue came live from the server and the Play lookup worked;
     * an incomplete load is tried again (see [ServerLoadGate]).
     */
    data class Snapshot(
        val entries: List<ServerEntry>,
        val apps: List<App>,
        val baseUrl: String,
        val complete: Boolean = false
    ) {

        /** True when a server was known (the Kho tab's cache exists). */
        val found: Boolean get() = baseUrl.isNotEmpty()

        private val entriesByPackage by lazy { entries.associateBy { it.app.packageName } }
        private val appsByPackage by lazy { apps.associateBy { it.packageName } }
        private val owned by lazy {
            entries.filter(ServerApps::isServerOwned).map { it.app.packageName }.toSet()
        }
        private val fileUrls by lazy {
            entries.map { it.app.downloadUrl }
                .filter { it.isNotEmpty() }
                .map { StoreCatalog.resolveUrl(baseUrl, it) }
                .toSet()
        }

        fun entry(packageName: String): ServerEntry? = entriesByPackage[packageName]

        fun app(packageName: String): App? = appsByPackage[packageName]

        fun ownedPackages(): Set<String> = owned

        fun isServerFileUrl(url: String): Boolean = url in fileUrls

        companion object {
            val EMPTY = Snapshot(emptyList(), emptyList(), "")
        }
    }

    private val mutex = Mutex()

    private val gate = ServerLoadGate(SystemClock::elapsedRealtime)

    // Loads started by ensureLoaded run here, not in the caller: a caller that stops waiting
    // (a details screen's timeout, a closed screen) must not cancel the load for everyone else.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var loading: Job? = null

    @Volatile
    private var snapshot = Snapshot.EMPTY

    private val _apps = MutableStateFlow<List<App>>(emptyList())

    /** The cluster list; empty until a load finds a server. */
    val apps: StateFlow<List<App>> = _apps.asStateFlow()

    /** Loads again from the server (or its cache). Never throws. */
    suspend fun refresh(): Unit = mutex.withLock { reload() }

    /**
     * Loads unless the last load was complete; an incomplete one is retried at most every
     * [ServerLoadGate.RETRY_GAP_MS]. The load runs in this repository's own scope, so cancelling
     * the caller (say, `withTimeoutOrNull`) only stops the wait.
     */
    suspend fun ensureLoaded() {
        if (!gate.shouldLoad(snapshot)) return
        startLoad().join()
    }

    private fun startLoad(): Job = synchronized(this) {
        loading?.takeIf { it.isActive } ?: scope.launch {
            mutex.withLock { if (gate.shouldLoad(snapshot)) reload() }
        }.also { loading = it }
    }

    fun entry(packageName: String): ServerEntry? = snapshot.entry(packageName)

    fun ownedPackages(): Set<String> = snapshot.ownedPackages()

    fun app(packageName: String): App? = snapshot.app(packageName)

    fun isServerFileUrl(url: String): Boolean = snapshot.isServerFileUrl(url)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun reload(): Unit = withContext(Dispatchers.IO) {
        gate.attempted()
        try {
            val dir = File(context.cacheDir, CACHE_DIR)
            val loaded = build(
                fetch = ::fetchText,
                cache = StoreCatalog.FileCatalogCache(dir),
                featuredCache = File(dir, FEATURED_FILE),
                language = Locale.getDefault().language,
                play = { appDetailsHelper.get().getAppByPackageName(it) },
                installed = ::installed
            )
            snapshot = loaded
            _apps.value = loaded.apps
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the last good load rather than empty the cluster.
            JstoreLog.w(TAG, "Could not load the Jaecoo server apps", e)
        }
    }

    private fun fetchText(url: String): String? = try {
        httpClient.call(url).use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    } catch (e: IOException) {
        JstoreLog.w(TAG, "Could not read $url", e)
        null
    } catch (e: IllegalArgumentException) {
        // OkHttp refuses a URL it cannot parse; an uncaught throw here would end jstore's process.
        JstoreLog.w(TAG, "Not a usable address: $url", e)
        null
    }

    /** What is installed now for [packageName]; null when it is not installed. */
    fun installed(packageName: String): Installed? = try {
        val info = PackageUtil.getPackageInfo(context, packageName)
        Installed(
            versionName = info.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            certHashes = CertUtil.getEncodedCertificateHashes(context, packageName)
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        private const val TAG = "JaecooServerApps"
        private const val CACHE_DIR = "jstore_store"
        private const val FEATURED_FILE = "featured.json"

        /**
         * One load, blocking: the catalogue (or its cache), the featured list (only its cache when
         * the catalogue came from the cache), then one Play lookup for every package.
         * [Snapshot.EMPTY] when no server was ever reached. A failing [play] counts as Play knowing
         * none of the apps, and makes the snapshot incomplete.
         */
        @Suppress("TooGenericExceptionCaught")
        internal fun build(
            fetch: (String) -> String?,
            cache: StoreCatalog.CatalogCache,
            featuredCache: File,
            language: String,
            play: (List<String>) -> List<App>,
            installed: (String) -> Installed?
        ): Snapshot {
            val (cachedBaseUrl, _) = cache.read() ?: return Snapshot.EMPTY
            val catalog = StoreCatalog(fetch, cache).load(listOf(cachedBaseUrl), language)
            val baseUrl = catalog?.baseUrl ?: cachedBaseUrl
            val live = catalog != null && !catalog.fromCache
            val featuredList = FeaturedList(fetch, featuredCache)
            // The server just failed to answer for the catalogue: use the featured cache only.
            val featured = if (live) {
                featuredList.load(baseUrl, language)
            } else {
                featuredList.cached(language)
            }
            val entries = ServerApps.entries(catalog?.apps.orEmpty(), featured)
            val packages = entries.map { it.app.packageName }
            var playOk = true
            val playApps: Map<String, App> = try {
                if (packages.isEmpty()) {
                    emptyMap()
                } else {
                    play(packages).associateBy { it.packageName }
                }
            } catch (e: Exception) {
                JstoreLog.w(TAG, "Play lookup for the Jaecoo server apps failed", e)
                playOk = false
                emptyMap()
            }
            val apps = ServerApps.assemble(entries, baseUrl, playApps, installed)
            return Snapshot(entries, apps, baseUrl, complete = live && playOk)
        }
    }
}
