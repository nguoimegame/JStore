/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.store.R
import com.aurora.store.jstore.JaecooConnection
import com.aurora.store.jstore.JstoreLog
import com.aurora.store.jstore.embed.StoreHostConfig
import com.aurora.store.jstore.featured.FeaturedList
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The Kho ứng dụng tab, as JaecooApp used to draw it and now drawn here: the Jaecoo server
 * catalogue, a card for Google Play (jstore itself), and the app manager. Everything it cannot
 * do from inside a surface (open an activity, show a toast, install, uninstall) it asks of
 * JaecooApp through [StoreScreenFactory.Host] or [JaecooConnection].
 */
@Suppress("TooManyFunctions", "LargeClass")
class StoreScreen(
    private val context: Context,
    private val config: StoreHostConfig,
    initialSection: String,
    private val host: StoreScreenFactory.Host,
    private val deps: StoreScreenFactory.Deps
) : StoreScreenFactory.Screen {

    private enum class Tab(val section: String) {
        LOCAL(StoreHostConfig.SECTION_JAECOO),
        MANAGE(StoreHostConfig.SECTION_MANAGE)
    }

    override val view: View =
        LayoutInflater.from(context).inflate(R.layout.jstore_store_fragment_layout, null, false)

    private val handler = Handler(Looper.getMainLooper())
    private val ops = StoreOps(context.applicationContext, deps.httpClient, deps.play)
    private val catalogSource =
        StoreCatalog(
            ::fetchText,
            StoreCatalog.FileCatalogCache(File(context.cacheDir, "jstore_store"))
        )

    private val featuredSource =
        FeaturedList(::fetchText, File(context.cacheDir, "jstore_store/featured.json"))

    private val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
    private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
    private val statusText: TextView = view.findViewById(R.id.statusText)
    private val panelLocal: View = view.findViewById(R.id.panelLocalStore)
    private val panelManage: View = view.findViewById(R.id.panelManageApps)
    private val btnFromPhone: View = view.findViewById(R.id.btnAppManagerFromPhone)
    private val btnOpenJstore: View = view.findViewById(R.id.btnOpenJstore)
    private val bannerNotice: View = view.findViewById(R.id.bannerLocalStoreNotice)
    private val btnNoticeAction: Button = view.findViewById(R.id.btnNoticeAction)
    private val segments: Map<Tab, TextView> = mapOf(
        Tab.LOCAL to view.findViewById(R.id.segStoreLocal),
        Tab.MANAGE to view.findViewById(R.id.segStoreManage)
    )

    private val statusByTab = mutableMapOf<Tab, Pair<CharSequence, Int>>()
    private var currentTab = Tab.LOCAL
    private var catalog: StoreCatalog.Catalog? = null

    // Catalogue plus the server's featured apps, for the app manager only. One reference, written
    // whole, so the update check (another thread) never sees a catalogue/featured mismatch.
    @Volatile
    private var managerCatalog: StoreCatalog.Catalog? = null
    private var allApps: List<StoreAppItem> = emptyList()
    private var adapter: StoreAppAdapter? = null
    private var serverBaseUrl = config.serverUrls.firstOrNull().orEmpty()
    private var noticeDismissed = false
    private var closed = false

    // Built after the state above: its first status line is written during construction.
    private val manage = ManagePanel(
        context = context,
        root = view,
        host = host,
        ops = ops,
        play = deps.play,
        catalogOf = { managerCatalog },
        jstoreRelease = { latestRelease() },
        onStatus = { text, colorRes -> setStatus(Tab.MANAGE, text, colorRes) },
        confirmUninstall = { app, onConfirmed ->
            confirmUninstall(app.packageName, app.label, onConfirmed)
        }
    )

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val changed = intent.data?.schemeSpecificPart ?: return
            onUi { onPackageChanged(changed) }
        }
    }

    init {
        // A surface window never enters touch mode, so Android would paint a focus highlight on
        // the first focusable view (the first tab) for good. The store has no keyboard focus UI.
        StoreViews.disableFocusHighlight(view)
        val columns = context.resources.getInteger(R.integer.jstore_store_app_list_columns)
        recyclerView.layoutManager =
            if (columns > 1) GridLayoutManager(context, columns) else LinearLayoutManager(context)

        btnOpenJstore.setOnClickListener { openJstore() }
        view.findViewById<View>(R.id.btnNoticeDismiss).setOnClickListener {
            noticeDismissed = true
            bannerNotice.visibility = View.GONE
        }
        btnNoticeAction.setOnClickListener { handleNoticeAction() }
        btnFromPhone.setOnClickListener { host.requestPhoneUpload() }
        view.findViewById<View>(R.id.btnRefreshApps).setOnClickListener { refreshCurrent() }
        statusText.setOnClickListener { refreshCurrent() }
        for ((tab, segment) in segments) segment.setOnClickListener { select(tab) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        select(Tab.values().firstOrNull { it.section == initialSection } ?: Tab.LOCAL)
        loadCatalog()
    }

    // ------------------------------------------------------------------ Screen

    override fun select(section: String) {
        select(Tab.values().firstOrNull { it.section == section } ?: Tab.LOCAL)
    }

    override fun currentSection(): String = currentTab.section

    override fun dispose() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        manage.dispose()
        StoreDialogResults.stopListening()
        try {
            context.applicationContext.unregisterReceiver(packageReceiver)
        } catch (_: IllegalArgumentException) {
            // Never registered, or already gone.
        }
    }

    private fun onUi(block: () -> Unit) {
        handler.post { if (!closed) block() }
    }

    // ------------------------------------------------------------------ tabs and status

    private fun select(tab: Tab) {
        currentTab = tab
        panelLocal.visibility = if (tab == Tab.LOCAL) View.VISIBLE else View.GONE
        panelManage.visibility = if (tab == Tab.MANAGE) View.VISIBLE else View.GONE
        btnFromPhone.visibility = if (tab == Tab.MANAGE) View.VISIBLE else View.GONE
        btnOpenJstore.visibility = if (tab == Tab.LOCAL) View.VISIBLE else View.GONE
        manage.setSelected(tab == Tab.MANAGE)

        for ((value, segment) in segments) {
            val isCurrent = value == tab
            segment.isSelected = isCurrent
            val background = if (isCurrent) {
                R.drawable.jstore_store_bg_radio_button
            } else {
                android.R.color.transparent
            }
            segment.setBackgroundResource(background)
            val textColor = if (isCurrent) {
                R.color.jstore_store_content_on_accent
            } else {
                R.color.jstore_store_content_secondary
            }
            segment.setTextColor(ContextCompat.getColor(context, textColor))
        }
        applyStatus()
    }

    private fun refreshCurrent() {
        when (currentTab) {
            Tab.LOCAL -> loadCatalog()
            Tab.MANAGE -> manage.reload(checkUpdates = true)
        }
    }

    /** Each tab keeps its own status line for when the driver switches back to it. */
    private fun setStatus(
        tab: Tab,
        text: CharSequence,
        colorRes: Int = R.color.jstore_store_content_secondary
    ) {
        statusByTab[tab] = text to colorRes
        if (tab == currentTab) applyStatus()
    }

    private fun applyStatus() {
        val (text, colorRes) = statusByTab[currentTab] ?: return
        statusText.text = text
        statusText.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    private fun onPackageChanged(changed: String) {
        if (allApps.any { it.packageName == changed }) {
            refreshInstalledStatus()
            adapter?.submitList(allApps)
        }
        if (changed == MICROG_PACKAGE || changed == GMS_PACKAGE) updateNoticeBanner()
        manage.reload(checkUpdates = false, onlyIfLoaded = true)
    }

    // ------------------------------------------------------------------ Jaecoo catalogue

    private fun loadCatalog() {
        progressBar.visibility = View.VISIBLE
        setStatus(Tab.LOCAL, context.getString(R.string.jstore_store_loading_apps))
        val language = config.language ?: Locale.getDefault().language
        thread(name = "store-catalog") {
            val loaded = catalogSource.load(config.serverUrls, language)
            val forManager = if (loaded != null && loaded.apps.isNotEmpty()) {
                ManageUpdates.withFeatured(loaded, featuredSource.load(loaded.baseUrl, language))
            } else {
                null
            }
            onUi {
                progressBar.visibility = View.GONE
                if (loaded != null && loaded.apps.isNotEmpty()) {
                    catalog = loaded
                    managerCatalog = forManager
                    serverBaseUrl = loaded.baseUrl
                    if (loaded.fromCache) {
                        setOffline()
                    } else {
                        setStatus(
                            Tab.LOCAL,
                            context.getString(
                                R.string.jstore_store_app_store_live_feed,
                                loaded.apps.size
                            ),
                            R.color.jstore_store_status_success
                        )
                    }
                    setupAdapter(loaded.apps.map(StoreAppItem::from))
                } else {
                    setOffline()
                    setupAdapter(emptyList())
                }
            }
        }
    }

    private fun setOffline() = setStatus(
        Tab.LOCAL,
        context.getString(
            R.string.jstore_store_app_store_offline_mode,
            context.getString(R.string.jstore_store_server_error)
        ),
        R.color.jstore_store_status_warning
    )

    private fun setupAdapter(apps: List<StoreAppItem>) {
        allApps = apps
        refreshInstalledStatus()
        updateNoticeBanner()
        val existing = adapter
        if (existing == null) {
            val created = StoreAppAdapter(allApps, serverBaseUrl) { handleAppAction(it) }
            created.onAppUninstall =
                { app -> confirmUninstall(app.packageName, app.name) { uninstall(app) } }
            adapter = created
            recyclerView.adapter = created
        } else {
            existing.serverBaseUrl = serverBaseUrl
            existing.submitList(allApps)
        }
    }

    private fun refreshInstalledStatus() {
        val packageManager = context.packageManager
        for (app in allApps) {
            if (app.packageName.isEmpty()) {
                // Nothing can be said without a package name; do not trust the catalogue's own flag.
                app.isInstalled = false
                app.installedVersion = null
                app.hasUpdate = false
                continue
            }
            val info = try {
                packageManager.getPackageInfo(app.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            if (info != null) {
                app.isInstalled = true
                val installed = info.versionName.orEmpty()
                app.installedVersion = installed
                app.hasUpdate = app.version.isNotEmpty() &&
                    installed.isNotEmpty() &&
                    ManageUpdates.isVersionNewer(app.version, installed)
            } else {
                // A ROM that filters package visibility answers NameNotFound for apps that are
                // there; the launcher intent declared in <queries> is the second opinion.
                app.isInstalled = packageManager.getLaunchIntentForPackage(app.packageName) != null
                app.installedVersion = null
                app.hasUpdate = false
            }
        }
    }

    private fun handleAppAction(app: StoreAppItem) {
        if (app.packageName == JAECOO_PACKAGE && (app.hasUpdate || !app.isInstalled)) {
            // JaecooApp updates itself; installing over it would end the screen that asked.
            host.startActivity(
                Intent().setClassName(JAECOO_PACKAGE, JAECOO_SHELL).putExtra(EXTRA_TAB, TAB_SYSTEM)
            )
            return
        }
        if (app.hasUpdate || !app.isInstalled) {
            if (app.downloadUrl.isNotEmpty()) {
                downloadAndInstall(app)
            } else {
                host.toast(
                    context.getString(R.string.jstore_store_appstore_no_download_url, app.name)
                )
            }
            return
        }
        val launch = if (app.packageName.isEmpty()) {
            null
        } else {
            context.packageManager.getLaunchIntentForPackage(
                app.packageName
            )
        }
        if (launch != null) {
            host.startActivity(launch)
        } else {
            host.toast(context.getString(R.string.jstore_store_appstore_opening_app, app.name))
        }
    }

    private fun downloadAndInstall(app: StoreAppItem) {
        if (app.isDownloading) return
        app.isDownloading = true
        app.downloadProgress = 0
        adapter?.notifyAppProgress(app.id, 0, true)

        val action = context.getString(
            if (app.hasUpdate) R.string.jstore_store_update_app else R.string.jstore_store_install
        ).lowercase()
        host.toast(
            context.getString(
                R.string.jstore_store_appstore_downloading_to_action,
                app.name,
                action
            )
        )

        val url = StoreCatalog.resolveUrl(serverBaseUrl, app.downloadUrl)
        thread(name = "store-install") {
            val apk = ops.download(
                url = url,
                packageName = app.packageName,
                fileName = StoreOps.fileNameFor(app.downloadUrl, app.id),
                estimatedBytes = StoreOps.parseEstimatedBytes(app.fileSize),
                sha256 = app.sha256
            ) { progress -> onUi { adapter?.notifyAppProgress(app.id, progress, true) } }
            val result = if (apk != null) {
                onUi {
                    adapter?.notifyAppProgress(app.id, StoreAppAdapter.PROGRESS_INSTALLING, true)
                }
                ops.install(app.packageName, apk)
            } else {
                null
            }
            onUi {
                app.isDownloading = false
                app.downloadProgress = 0
                adapter?.notifyAppProgress(app.id, 0, false)
                reportInstall(result)
            }
        }
    }

    private fun reportInstall(result: JaecooConnection.Result?) {
        when (result) {
            is JaecooConnection.Result.Installed -> {
                host.toast(
                    context.getString(R.string.jstore_store_appstore_device_owner_install_success)
                )
                // A silent install never leaves the screen, so the buttons must be re-read here.
                refreshInstalledStatus()
                adapter?.submitList(allApps)
                updateNoticeBanner()
            }
            is JaecooConnection.Result.Launched -> host.toast(
                context.getString(R.string.jstore_store_installing_apk)
            )
            is JaecooConnection.Result.Failed ->
                host.toast(
                    context.getString(R.string.jstore_store_appstore_install_error, result.message)
                )
            JaecooConnection.Result.Unavailable -> host.toast(
                context.getString(R.string.jstore_jaecooapp_required)
            )
            JaecooConnection.Result.Uninstalled -> Unit
            null -> host.toast(context.getString(R.string.jstore_store_appstore_download_failed))
        }
    }

    // ------------------------------------------------------------------ uninstall

    /** A window cannot open inside the surface: JaecooApp starts the confirmation activity for us. */
    private fun confirmUninstall(packageName: String, label: String, onConfirmed: () -> Unit) {
        if (packageName.isEmpty()) {
            host.toast(context.getString(R.string.jstore_store_appstore_unknown_package, label))
            return
        }
        StoreDialogResults.listen { answered, confirmed ->
            if (answered == packageName && confirmed) onUi(onConfirmed)
        }
        host.startActivity(StoreDialogActivity.confirmUninstallIntent(context, packageName, label))
    }

    private fun uninstall(app: StoreAppItem) {
        host.toast(context.getString(R.string.jstore_store_uninstalling, app.name))
        thread(name = "store-uninstall") {
            val result = JaecooConnection.uninstall(context, app.packageName)
            onUi {
                when (result) {
                    is JaecooConnection.Result.Uninstalled -> {
                        host.toast(
                            context.getString(R.string.jstore_store_uninstall_done, app.name)
                        )
                        refreshInstalledStatus()
                        adapter?.submitList(allApps)
                        updateNoticeBanner()
                    }
                    is JaecooConnection.Result.Failed ->
                        host.toast(
                            context.getString(
                                R.string.jstore_store_uninstall_failed,
                                app.name,
                                result.message
                            )
                        )
                    else -> Unit
                }
            }
        }
    }

    // ------------------------------------------------------------------ MicroG notice

    private fun updateNoticeBanner() {
        if (noticeDismissed) {
            bannerNotice.visibility = View.GONE
            return
        }
        bannerNotice.visibility = View.VISIBLE
        btnNoticeAction.setText(
            if (microGInstalled()) {
                R.string.jstore_store_jaecoo_store_notice_action_open
            } else {
                R.string.jstore_store_jaecoo_store_notice_action_install
            }
        )
    }

    private fun microGInstalled() =
        isPackageInstalled(MICROG_PACKAGE) || isPackageInstalled(GMS_PACKAGE)

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    private fun handleNoticeAction() {
        if (microGInstalled()) {
            val pm = context.packageManager
            val launch =
                pm.getLaunchIntentForPackage(MICROG_PACKAGE)
                    ?: pm.getLaunchIntentForPackage(GMS_PACKAGE)
            host.startActivity(launch ?: Intent(Settings.ACTION_SYNC_SETTINGS))
            return
        }
        val microg = allApps.firstOrNull {
            it.id == "microg-re" || it.packageName == MICROG_PACKAGE
        }
        if (microg != null) {
            handleAppAction(microg)
        } else {
            host.toast(context.getString(R.string.jstore_store_loading_apps))
        }
    }

    // ------------------------------------------------------------------ jstore button

    /** Opens jstore's own Google Play screens; JaecooApp starts the activity (the surface cannot). */
    private fun openJstore() {
        context.packageManager.getLaunchIntentForPackage(
            context.packageName
        )?.let(host::startActivity)
    }

    /** GitHub's latest jstore release, or null when it cannot be reached. Blocking. */
    private fun latestRelease(): JstoreRelease? = JstoreReleases.latest { url ->
        fetchText(url, mapOf("Accept" to "application/vnd.github+json"))
    }

    private fun fetchText(url: String): String? = fetchText(url, emptyMap())

    private fun fetchText(url: String, headers: Map<String, String>): String? = try {
        deps.httpClient.call(url, headers).use { response ->
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

    companion object {
        private const val TAG = "StoreScreen"
        private const val MICROG_PACKAGE = "app.revanced.android.gms"
        private const val GMS_PACKAGE = "com.google.android.gms"
        private const val JAECOO_PACKAGE = "com.example.jaecooapp"
        private const val JAECOO_SHELL = "com.example.jaecooapp.ShellActivity"
        private const val EXTRA_TAB = "tab"
        private const val TAB_SYSTEM = "system"
    }
}
