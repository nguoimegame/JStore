/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.store.R
import com.aurora.store.jstore.JaecooConnection
import com.aurora.store.jstore.ManagedApp
import com.aurora.store.jstore.PlayUpdates
import kotlin.concurrent.thread

/**
 * The app manager tab: every app the driver installed, with what can be done to it. Update it
 * when the Jaecoo store, GitHub (jstore) or Google Play has a newer release, open it, uninstall
 * it, and choose whether it stays on the launcher.
 *
 * Hiding uninstalls nothing: a hidden app keeps its data and comes back the moment its switch
 * goes on again. JaecooApp does it with Device Owner; without it the switches are inert and the
 * status line says why. Needs JaecooApp's installer v2; an older one gets a notice instead.
 */
@Suppress("TooManyFunctions")
class ManagePanel(
    private val context: Context,
    root: View,
    private val host: StoreScreenFactory.Host,
    private val ops: StoreOps,
    private val play: PlayUpdates,
    private val catalogOf: () -> StoreCatalog.Catalog?,
    private val jstoreRelease: () -> JstoreRelease?,
    private val onStatus: (CharSequence, Int) -> Unit,
    private val confirmUninstall: (ManagedApp, onConfirmed: () -> Unit) -> Unit
) : ManageAdapter.Actions {

    private val handler = Handler(Looper.getMainLooper())
    private val recyclerView: RecyclerView = root.findViewById(R.id.recyclerAppManager)
    private val progressBar: ProgressBar = root.findViewById(R.id.progressAppManager)
    private val emptyText: TextView = root.findViewById(R.id.tvAppManagerEmpty)

    private var adapter: ManageAdapter? = null
    private var apps: List<ManagedApp> = emptyList()
    private var canManage = false
    private var supported = true
    private var updates: Map<String, ManageUpdates.Update> = emptyMap()
    private var checkingUpdates = false
    private var isApplying = false
    private var hasLoaded = false
    private var needsReload = false
    private var isSelected = false
    private var closed = false

    init {
        val columns = context.resources.getInteger(R.integer.jstore_store_app_list_columns)
        recyclerView.layoutManager =
            if (columns > 1) GridLayoutManager(context, columns) else LinearLayoutManager(context)
        onStatus(
            context.getString(R.string.jstore_store_app_visibility_loading),
            R.color.jstore_store_content_secondary
        )
    }

    fun setSelected(selected: Boolean) {
        isSelected = selected
        if (!selected) return
        if (!hasLoaded) {
            loadApps(checkUpdates = true)
        } else if (needsReload) {
            needsReload = false
            loadApps(checkUpdates = false)
        }
    }

    /** Re-lists the apps. When the tab is not showing, waits until it is. */
    fun reload(checkUpdates: Boolean, onlyIfLoaded: Boolean = false) {
        if (onlyIfLoaded && !hasLoaded) return
        if (onlyIfLoaded && !isSelected) {
            needsReload = true
            return
        }
        needsReload = false
        loadApps(checkUpdates)
    }

    fun dispose() {
        closed = true
        handler.removeCallbacksAndMessages(null)
    }

    private fun onUi(block: () -> Unit) {
        handler.post { if (!closed) block() }
    }

    /**
     * The update check goes to the network, so it only runs on first show and on refresh; a
     * plain re-list just drops updates that have since been installed.
     */
    private fun loadApps(checkUpdates: Boolean) {
        progressBar.visibility = View.VISIBLE
        thread(name = "store-manage-list") {
            val api = JaecooConnection.installerApiVersion(context)
            val isSupported = JaecooConnection.supportsManager(api)
            val loaded = if (isSupported) JaecooConnection.listApps(context) else emptyList()
            val owner = isSupported && JaecooConnection.canManageApps(context)
            onUi {
                hasLoaded = true
                progressBar.visibility = View.GONE
                supported = isSupported
                apps = loaded
                canManage = owner
                updates = updates.filterKeys { key -> loaded.any { it.packageName == key } }
                render()
                if (checkUpdates && !checkingUpdates && apps.isNotEmpty()) startUpdateCheck()
            }
        }
    }

    private fun startUpdateCheck() {
        checkingUpdates = true
        render()
        val snapshot = apps
        thread(name = "store-manage-updates") {
            val found = ManageUpdates.pick(
                apps = snapshot,
                jstoreRelease = jstoreRelease(),
                catalog = catalogOf(),
                play = { play.findLatestVersions(it) }
            )
            onUi {
                checkingUpdates = false
                updates = found.filterKeys { key -> apps.any { it.packageName == key } }
                render()
            }
        }
    }

    private fun render() {
        emptyText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        if (!supported) {
            emptyText.setText(R.string.jstore_store_needs_jaecooapp)
            onStatus(
                context.getString(R.string.jstore_store_needs_jaecooapp),
                R.color.jstore_store_status_warning
            )
        } else {
            emptyText.setText(R.string.jstore_store_app_visibility_empty)
        }

        val existing = adapter
        if (existing == null) {
            val created = ManageAdapter(apps, this)
            created.togglesEnabled = canManage
            created.updates = updates
            adapter = created
            recyclerView.adapter = created
        } else {
            existing.togglesEnabled = canManage
            existing.updates = updates
            existing.submitList(apps)
        }
        if (!supported) return

        val parts = mutableListOf(
            context.getString(
                R.string.jstore_store_app_manager_subtitle,
                apps.size,
                apps.count { it.hidden },
                updates.size
            )
        )
        if (checkingUpdates) {
            parts +=
                context.getString(R.string.jstore_store_app_manager_checking_updates)
        }
        if (!canManage) {
            parts +=
                context.getString(R.string.jstore_store_app_manager_visibility_needs_owner)
        }
        onStatus(parts.joinToString(" · "), R.color.jstore_store_content_secondary)
    }

    // ------------------------------------------------------------------ actions

    override fun onOpen(app: ManagedApp) {
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch == null) {
            host.toast(context.getString(R.string.jstore_store_app_manager_open_failed, app.label))
        } else {
            host.startActivity(launch)
        }
    }

    override fun onUninstall(app: ManagedApp) {
        if (app.protected) return
        confirmUninstall(app) {
            host.toast(context.getString(R.string.jstore_store_uninstalling, app.label))
            thread(name = "store-uninstall") {
                val result = JaecooConnection.uninstall(context, app.packageName)
                onUi {
                    when (result) {
                        is JaecooConnection.Result.Uninstalled -> {
                            host.toast(
                                context.getString(R.string.jstore_store_uninstall_done, app.label)
                            )
                            loadApps(checkUpdates = false)
                        }
                        is JaecooConnection.Result.Failed -> host.toast(
                            context.getString(
                                R.string.jstore_store_uninstall_failed,
                                app.label,
                                result.message
                            )
                        )
                        else -> loadApps(checkUpdates = false)
                    }
                }
            }
        }
    }

    override fun onToggle(app: ManagedApp, visible: Boolean) {
        if (isApplying || app.protected) return
        isApplying = true
        val hidden = !visible
        thread(name = "store-toggle") {
            val ok = JaecooConnection.setHidden(context, app.packageName, hidden)
            val refreshed = JaecooConnection.listApps(context)
            onUi {
                isApplying = false
                apps = refreshed
                render()
                host.toast(
                    when {
                        !ok -> context.getString(
                            R.string.jstore_store_app_visibility_toggle_failed,
                            app.label
                        )
                        hidden -> context.getString(
                            R.string.jstore_store_app_visibility_hidden_one,
                            app.label
                        )
                        else -> context.getString(
                            R.string.jstore_store_app_visibility_shown_one,
                            app.label
                        )
                    }
                )
            }
        }
    }

    override fun onUpdate(app: ManagedApp, update: ManageUpdates.Update) {
        val rows = adapter ?: return
        if (rows.isBusy(app.packageName)) return
        if (update.source == ManageUpdates.Source.JSTORE) {
            // jstore replacing itself ends this process: JaecooApp takes the screen down first.
            host.requestJstoreUpdate()
            return
        }
        rows.setProgress(app.packageName, 0)
        host.toast(context.getString(R.string.jstore_store_app_manager_updating, app.label))
        thread(name = "store-update") {
            val outcome = runCatching { install(app, update) }
            onUi {
                adapter?.setProgress(app.packageName, null)
                outcome.onSuccess { result -> reportInstall(app, result) }
                    .onFailure { error ->
                        host.toast(
                            context.getString(
                                R.string.jstore_store_app_manager_update_failed,
                                app.label,
                                error.message ?: error.javaClass.simpleName
                            )
                        )
                    }
            }
        }
    }

    private fun install(app: ManagedApp, update: ManageUpdates.Update): JaecooConnection.Result {
        val onProgress = { progress: Int ->
            onUi { adapter?.setProgress(app.packageName, progress) }
        }
        val onInstalling = {
            onUi { adapter?.setProgress(app.packageName, StoreAppAdapter.PROGRESS_INSTALLING) }
        }
        return when (update.source) {
            ManageUpdates.Source.PLAY -> ops.installFromPlay(
                app.packageName,
                onProgress,
                onInstalling
            )
            else -> {
                val apk = ops.download(
                    url = update.downloadUrl,
                    packageName = app.packageName,
                    fileName = StoreOps.fileNameFor(update.downloadUrl, app.packageName),
                    estimatedBytes = StoreOps.parseEstimatedBytes(update.fileSize),
                    sha256 = update.sha256,
                    onProgress = onProgress
                ) ?: error(context.getString(R.string.jstore_store_appstore_download_failed))
                onInstalling()
                ops.install(app.packageName, apk)
            }
        }
    }

    private fun reportInstall(app: ManagedApp, result: JaecooConnection.Result) {
        when (result) {
            is JaecooConnection.Result.Installed -> {
                host.toast(context.getString(R.string.jstore_store_app_manager_updated, app.label))
                loadApps(checkUpdates = false)
            }
            is JaecooConnection.Result.Launched -> host.toast(
                context.getString(R.string.jstore_store_installing_apk)
            )
            is JaecooConnection.Result.Failed -> host.toast(
                context.getString(R.string.jstore_store_appstore_install_error, result.message)
            )
            else -> host.toast(context.getString(R.string.jstore_jaecooapp_required))
        }
    }
}
