/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aurora.store.R

class StoreAppAdapter(
    private var apps: List<StoreAppItem>,
    serverBaseUrl: String,
    private val onAppAction: (StoreAppItem) -> Unit
) : RecyclerView.Adapter<StoreAppAdapter.ViewHolder>() {

    var serverBaseUrl: String = serverBaseUrl
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    /**
     * Bấm nút "Xóa". Để null thì nút gỡ không hiện — màn hình nào không xử lý được
     * việc gỡ thì đơn giản là không gắn callback.
     */
    var onAppUninstall: ((StoreAppItem) -> Unit)? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val statusBadge: TextView = view.findViewById(R.id.appStatusBadge)
        val meta: TextView = view.findViewById(R.id.appMeta)
        val description: TextView = view.findViewById(R.id.appDescription)
        val containerDownload: View = view.findViewById(R.id.containerDownloadProgress)
        val pbDownload: ProgressBar = view.findViewById(R.id.pbItemDownload)
        val tvDownloadProgress: TextView = view.findViewById(R.id.tvItemDownloadProgress)
        val installButton: Button = view.findViewById(R.id.installButton)
        val uninstallButton: Button = view.findViewById(R.id.uninstallButton)
    }

    fun submitList(newList: List<StoreAppItem>) {
        this.apps = newList
        notifyDataSetChanged()
    }

    fun notifyAppProgress(appId: String, progress: Int, isDownloading: Boolean) {
        val index = apps.indexOfFirst { it.id == appId }
        if (index != -1) {
            val app = apps[index]
            app.downloadProgress = progress
            app.isDownloading = isDownloading
            notifyItemChanged(index, PAYLOAD_PROGRESS)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(
            parent.context
        ).inflate(R.layout.jstore_store_item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PROGRESS)) {
            val app = apps[position]
            bindProgressOnly(holder, app)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context

        holder.name.text = app.name
        holder.description.text = app.description

        // 1. Phân giải Icon chuyên biệt cho từng app hoặc nạp từ hệ thống Android
        bindAppIcon(holder, app)

        // 2. Trạng thái Badge & Metadata phiên bản
        if (app.hasUpdate) {
            holder.statusBadge.visibility = View.VISIBLE
            holder.statusBadge.text = context.getString(R.string.jstore_store_update_available)
            holder.statusBadge.setBackgroundResource(R.drawable.jstore_store_bg_pill_badge_amber)
            holder.statusBadge.setTextColor(
                ContextCompat.getColor(context, R.color.jstore_store_status_warning)
            )

            val fromVer = app.installedVersion?.let { "v$it" } ?: ""
            val toVer = if (app.version.isNotEmpty()) "v${app.version}" else ""
            val verDiff = if (fromVer.isNotEmpty() &&
                toVer.isNotEmpty()
            ) {
                "$fromVer → $toVer"
            } else {
                toVer
            }
            val metaList = listOfNotNull(
                verDiff.ifEmpty { null },
                app.category.ifEmpty { null },
                app.fileSize.ifEmpty { null }
            )
            holder.meta.text = metaList.joinToString(" • ")
        } else if (app.isInstalled) {
            holder.statusBadge.visibility = View.VISIBLE
            holder.statusBadge.text = context.getString(R.string.jstore_store_installed)
            holder.statusBadge.setBackgroundResource(R.drawable.jstore_store_bg_pill_badge_success)
            holder.statusBadge.setTextColor(
                ContextCompat.getColor(context, R.color.jstore_store_status_success)
            )

            val currentVer = (app.installedVersion ?: app.version).let {
                if (it.isNotEmpty()) "v$it" else null
            }
            val metaList = listOfNotNull(
                currentVer,
                app.category.ifEmpty { null }
            )
            holder.meta.text = metaList.joinToString(" • ")
        } else {
            holder.statusBadge.visibility = View.GONE
            val metaList = listOfNotNull(
                app.version.ifEmpty { null }?.let { "v$it" },
                app.category.ifEmpty { null },
                app.fileSize.ifEmpty { null }
            )
            holder.meta.text = metaList.joinToString(" • ")
        }

        // 3. Tiến trình Download & Trạng thái Nút bấm
        bindProgressAndButton(holder, app)

        // 4. Sự kiện click
        holder.installButton.setOnClickListener {
            if (!app.isDownloading) {
                onAppAction(app)
            }
        }

        holder.uninstallButton.setOnClickListener {
            if (!app.isDownloading) {
                onAppUninstall?.invoke(app)
            }
        }
    }

    private fun bindAppIcon(holder: ViewHolder, app: StoreAppItem) {
        StoreIconLoader.load(holder.icon, app, serverBaseUrl)
    }

    private fun bindProgressOnly(holder: ViewHolder, app: StoreAppItem) {
        val context = holder.itemView.context
        if (app.isDownloading) {
            bindDownloadState(holder, app)
            holder.uninstallButton.visibility = View.GONE
        } else {
            holder.containerDownload.visibility = View.GONE
            bindProgressAndButton(holder, app)
        }
    }

    private fun bindProgressAndButton(holder: ViewHolder, app: StoreAppItem) {
        val context = holder.itemView.context

        // Chỉ app đã có trên máy mới gỡ được, và không gỡ giữa chừng lúc đang tải.
        holder.uninstallButton.visibility =
            if (app.isInstalled && !app.isDownloading && onAppUninstall != null) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (app.isDownloading) {
            bindDownloadState(holder, app)
            holder.installButton.setBackgroundResource(R.drawable.jstore_store_bg_button_accent)
            holder.installButton.setTextColor(
                ContextCompat.getColor(context, R.color.jstore_store_content_on_accent)
            )
        } else {
            holder.containerDownload.visibility = View.GONE
            holder.installButton.isEnabled = true

            if (app.hasUpdate) {
                // Hiển thị nút Cập nhật (Amber Badge Gradient)
                holder.installButton.text = context.getString(R.string.jstore_store_update_app)
                holder.installButton.setBackgroundResource(
                    R.drawable.jstore_store_bg_button_warning
                )
                holder.installButton.setTextColor(
                    ContextCompat.getColor(context, R.color.jstore_store_content_on_accent)
                )
            } else if (app.isInstalled) {
                // Hiển thị nút Mở (Secondary)
                holder.installButton.text = context.getString(R.string.jstore_store_open_app)
                holder.installButton.setBackgroundResource(
                    R.drawable.jstore_store_bg_button_secondary
                )
                holder.installButton.setTextColor(
                    ContextCompat.getColor(context, R.color.jstore_store_content_primary)
                )
            } else {
                // Hiển thị nút Cài đặt (Accent Gradient)
                holder.installButton.text = context.getString(R.string.jstore_store_install)
                holder.installButton.setBackgroundResource(R.drawable.jstore_store_bg_button_accent)
                holder.installButton.setTextColor(
                    ContextCompat.getColor(context, R.color.jstore_store_content_on_accent)
                )
            }
        }
    }

    override fun getItemCount() = apps.size

    /** Đang tải thì hiện %, tải xong chuyển sang cài thì hiện thanh chạy vô định + "Đang cài đặt…". */
    private fun bindDownloadState(holder: ViewHolder, app: StoreAppItem) {
        val context = holder.itemView.context
        holder.containerDownload.visibility = View.VISIBLE
        holder.installButton.isEnabled = false
        if (app.downloadProgress == PROGRESS_INSTALLING) {
            holder.pbDownload.isIndeterminate = true
            holder.tvDownloadProgress.text = context.getString(R.string.jstore_store_installing_app)
            holder.installButton.text = context.getString(R.string.jstore_store_installing_short)
        } else {
            holder.pbDownload.isIndeterminate = false
            holder.pbDownload.progress = app.downloadProgress
            holder.tvDownloadProgress.text =
                context.getString(R.string.jstore_store_downloading_percent, app.downloadProgress)
            holder.installButton.text = "${app.downloadProgress}%"
        }
    }

    companion object {
        /** Giá trị downloadProgress báo hiệu đã tải xong và đang cài APK. */
        const val PROGRESS_INSTALLING = -1
        private const val PAYLOAD_PROGRESS = "payload_progress"
    }
}
