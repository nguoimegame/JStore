/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.aurora.store.R
import com.aurora.store.jstore.ManagedApp

/**
 * The installed-app list of the app manager tab. Each row can update, open or uninstall its
 * app, and carries the launcher switch. The switch reads as "on the launcher", so checked is
 * the visible state, the inverse of [ManagedApp.hidden]. A [ManagedApp.protected] app (jstore
 * itself) keeps its switch off the table and has no uninstall.
 */
class ManageAdapter(
    private var apps: List<ManagedApp>,
    private val actions: Actions
) : RecyclerView.Adapter<ManageAdapter.ViewHolder>() {

    /** What the row buttons ask the panel to do. */
    interface Actions {
        fun onToggle(app: ManagedApp, visible: Boolean)
        fun onUpdate(app: ManagedApp, update: ManageUpdates.Update)
        fun onOpen(app: ManagedApp)
        fun onUninstall(app: ManagedApp)
    }

    /** Greys out every switch when JaecooApp cannot hide apps. */
    var togglesEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    /** Newer releases found for installed apps, by package. */
    var updates: Map<String, ManageUpdates.Update> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Apps with an update in flight: 0-100 while downloading, or [StoreAppAdapter.PROGRESS_INSTALLING]. */
    private val progress = mutableMapOf<String, Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val updateBadge: TextView = view.findViewById(R.id.appUpdateBadge)
        val hiddenBadge: TextView = view.findViewById(R.id.appHiddenBadge)
        val version: TextView = view.findViewById(R.id.appVersion)
        val packageName: TextView = view.findViewById(R.id.appPackage)
        val progressContainer: View = view.findViewById(R.id.containerUpdateProgress)
        val progressBar: ProgressBar = view.findViewById(R.id.pbUpdate)
        val progressText: TextView = view.findViewById(R.id.tvUpdateProgress)
        val updateButton: Button = view.findViewById(R.id.btnUpdate)
        val openButton: Button = view.findViewById(R.id.btnOpen)
        val uninstallButton: Button = view.findViewById(R.id.btnUninstall)
        val switch: SwitchCompat = view.findViewById(R.id.switchVisible)
    }

    fun submitList(newList: List<ManagedApp>) {
        apps = newList
        notifyDataSetChanged()
    }

    fun isBusy(packageName: String) = packageName in progress

    /** Pass null to clear the row once its update is done or failed. */
    fun setProgress(packageName: String, value: Int?) {
        if (value == null) progress.remove(packageName) else progress[packageName] = value
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index != -1) notifyItemChanged(index, PAYLOAD_PROGRESS)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.jstore_store_item_app_manager, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PROGRESS)) {
            bindActions(holder, apps[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        val update = updates[app.packageName]

        holder.name.text = app.label
        holder.packageName.text = app.packageName
        bindIcon(holder, app)
        holder.hiddenBadge.visibility = if (app.hidden) View.VISIBLE else View.GONE
        holder.updateBadge.visibility = if (update != null) View.VISIBLE else View.GONE

        val installed = app.versionName.ifEmpty { null }?.let { "v$it" }
        holder.version.text = if (update != null) {
            val source = when (update.source) {
                ManageUpdates.Source.JAECOO -> context.getString(
                    R.string.jstore_store_app_manager_source_jaecoo
                )
                ManageUpdates.Source.PLAY -> SOURCE_PLAY
                ManageUpdates.Source.JSTORE -> SOURCE_GITHUB
            }
            listOfNotNull(
                listOfNotNull(installed, "v${update.versionName}").joinToString(" → "),
                source,
                update.fileSize.ifEmpty { null }
            ).joinToString(" • ")
        } else {
            installed.orEmpty()
        }
        holder.version.visibility = if (holder.version.text.isEmpty()) View.GONE else View.VISIBLE

        bindActions(holder, app)

        holder.updateButton.setOnClickListener {
            val current = updates[app.packageName] ?: return@setOnClickListener
            if (!isBusy(app.packageName)) actions.onUpdate(app, current)
        }
        holder.openButton.setOnClickListener { actions.onOpen(app) }
        holder.uninstallButton.setOnClickListener {
            if (!isBusy(app.packageName)) actions.onUninstall(app)
        }

        // Detach first: a recycled row still carries the previous app's listener,
        // and setting the state below would otherwise fire a toggle for it.
        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = !app.hidden
        val switchEnabled = togglesEnabled && !app.protected
        holder.switch.isEnabled = switchEnabled
        holder.switch.alpha = if (switchEnabled) 1f else DISABLED_ALPHA
        holder.switch.contentDescription =
            context.getString(R.string.jstore_store_app_visibility_switch_cd, app.label)
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            actions.onToggle(app, isChecked)
        }
    }

    /** A hidden app has no launcher entry for PackageManager to give its icon, so JaecooApp sends one. */
    private fun bindIcon(holder: ViewHolder, app: ManagedApp) {
        val sent = app.icon?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (sent != null) {
            holder.icon.setImageBitmap(sent)
            return
        }
        try {
            holder.icon.setImageDrawable(
                holder.itemView.context.packageManager.getApplicationIcon(app.packageName)
            )
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            holder.icon.setImageResource(R.drawable.jstore_store_ic_apps)
        }
    }

    /** Buttons and the progress strip: everything an update in flight changes. */
    private fun bindActions(holder: ViewHolder, app: ManagedApp) {
        val context = holder.itemView.context
        val busy = progress[app.packageName]

        if (busy != null) {
            holder.progressContainer.visibility = View.VISIBLE
            if (busy == StoreAppAdapter.PROGRESS_INSTALLING) {
                holder.progressBar.isIndeterminate = true
                holder.progressText.text = context.getString(R.string.jstore_store_installing_app)
                holder.updateButton.text = context.getString(R.string.jstore_store_installing_short)
            } else {
                holder.progressBar.isIndeterminate = false
                holder.progressBar.progress = busy
                holder.progressText.text =
                    context.getString(R.string.jstore_store_downloading_percent, busy)
                holder.updateButton.text = "$busy%"
            }
        } else {
            holder.progressContainer.visibility = View.GONE
            holder.updateButton.text = context.getString(R.string.jstore_store_update_app)
        }

        holder.updateButton.visibility =
            if (busy != null || updates.containsKey(app.packageName)) View.VISIBLE else View.GONE
        holder.updateButton.isEnabled = busy == null

        // A hidden app has no launcher entry to start, so there is nothing to open.
        val canOpen = !app.hidden && busy == null
        holder.openButton.isEnabled = canOpen
        holder.openButton.alpha = if (canOpen) 1f else DISABLED_ALPHA

        val canUninstall = busy == null && !app.protected
        holder.uninstallButton.isEnabled = canUninstall
        holder.uninstallButton.alpha = if (canUninstall) 1f else DISABLED_ALPHA
    }

    override fun getItemCount() = apps.size

    companion object {
        private const val PAYLOAD_PROGRESS = "payload_progress"
        private const val DISABLED_ALPHA = 0.4f
        private const val SOURCE_PLAY = "Google Play"
        private const val SOURCE_GITHUB = "GitHub"
    }
}
