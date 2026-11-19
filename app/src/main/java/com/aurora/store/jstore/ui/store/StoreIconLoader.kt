/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.aurora.store.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Loads the icon of a catalogue app: the installed app's own icon first, then the server's
 * (kept in memory and on disk so the list scrolls smoothly and shows offline), then a
 * bundled fallback. The view's tag guards against a recycled row getting a stale icon.
 */
object StoreIconLoader {

    private const val CONNECT_TIMEOUT_MS = 3500
    private const val READ_TIMEOUT_MS = 4000
    private const val POOL_SIZE = 4
    private const val KB = 1024
    private const val HEAP_SHARE = 8
    private const val MIN_CACHE_KB = 1024

    private val memoryCache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / KB).toInt() / HEAP_SHARE).coerceAtLeast(MIN_CACHE_KB)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / KB).coerceAtLeast(1)
    }

    private val executor = Executors.newFixedThreadPool(POOL_SIZE)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The full URL of [iconUrl] on the server that answered; empty when there is none. */
    fun resolveUrl(iconUrl: String?, serverBaseUrl: String): String {
        val trimmed = iconUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val path = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return serverBaseUrl.trim().trimEnd('/') + path
    }

    fun load(imageView: ImageView, app: StoreAppItem, serverBaseUrl: String) {
        val context = imageView.context
        if (app.isInstalled &&
            app.packageName.isNotEmpty() &&
            showInstalledIcon(imageView, app.packageName)
        ) {
            return
        }

        val url = resolveUrl(app.iconUrl, serverBaseUrl)
        val cached = if (url.isEmpty()) null else memoryCache.get(url)
        if (cached != null) {
            imageView.tag = url
            imageView.setImageBitmap(cached)
            imageView.imageTintList = null
            return
        }

        val fallback = fallbackIcon(app)
        if (fallback != null) {
            imageView.setImageResource(fallback)
            imageView.imageTintList = null
        } else {
            imageView.setImageResource(R.drawable.jstore_store_ic_apps)
            imageView.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.jstore_store_icon_active)
            )
        }
        if (url.isEmpty()) {
            imageView.tag = null
            return
        }
        imageView.tag = url
        val diskFile = diskCacheFile(imageView, url)
        executor.execute {
            val bitmap = decodeFile(diskFile) ?: download(url, diskFile)
            if (bitmap != null) {
                memoryCache.put(url, bitmap)
                mainHandler.post {
                    if (imageView.tag == url) {
                        imageView.setImageBitmap(bitmap)
                        imageView.imageTintList = null
                    }
                }
            }
        }
    }

    private fun showInstalledIcon(imageView: ImageView, packageName: String): Boolean = try {
        imageView.tag = null
        imageView.setImageDrawable(imageView.context.packageManager.getApplicationIcon(packageName))
        imageView.imageTintList = null
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private fun diskCacheFile(imageView: ImageView, url: String): File {
        val dir = File(imageView.context.cacheDir, "jstore_store_icons").apply { mkdirs() }
        val hash = (url.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        return File(dir, "icon_$hash")
    }

    private fun decodeFile(file: File): Bitmap? {
        if (!file.isFile || file.length() == 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath) ?: run {
            file.delete()
            null
        }
    }

    private fun download(url: String, target: File): Bitmap? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "image/webp,image/png,image/*;q=0.8")
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                try {
                    target.writeBytes(bytes)
                } catch (_: java.io.IOException) {
                    // No disk cache: the icon is fetched again next time.
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: java.io.IOException) {
        null
    }

    private fun fallbackIcon(app: StoreAppItem): Int? = when {
        app.packageName == "com.google.android.gms" || app.id.contains("microg", true) ->
            R.drawable.jstore_store_ic_app_microg
        app.packageName == "com.google.android.youtube" || app.id.contains("youtube", true) ->
            R.drawable.jstore_store_ic_app_youtube
        app.packageName == "com.google.android.apps.maps" || app.id.contains("map", true) ->
            R.drawable.jstore_store_ic_app_maps
        app.packageName == "com.apkpure.aegon" || app.id.contains("apkpure", true) ->
            R.drawable.jstore_store_ic_app_apkpure
        app.packageName == "vn.vietmap.live" || app.id.contains("vietmap", true) ->
            R.drawable.jstore_store_ic_app_vietmap
        else -> null
    }
}
