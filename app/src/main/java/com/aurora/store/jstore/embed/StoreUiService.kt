/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.embed

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.aurora.store.data.network.HttpClient
import com.aurora.store.jstore.JaecooConnection
import com.aurora.store.jstore.JstoreLog
import com.aurora.store.jstore.PlayUpdates
import com.aurora.store.jstore.ui.store.StoreScreenFactory
import com.example.jstore.store.IJstoreStoreUi
import com.example.jstore.store.IJstoreStoreUiCallback
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * The store screen (Jaecoo catalogue, Google Play, app manager) drawn here and shown inside
 * JaecooApp through a SurfaceControlViewHost. See docs/jstore-contract.md, IJstoreStoreUi.
 * Same approach as JaecooAssistant's AssistantUiService.
 */
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.R)
class StoreUiService : Service() {

    @Inject
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var play: PlayUpdates

    private val mainHandler = Handler(Looper.getMainLooper())

    private var viewHost: SurfaceControlViewHost? = null
    private var container: FrameLayout? = null
    private var screen: StoreScreenFactory.Screen? = null
    private var hostConfig = StoreHostConfig.fromMap(emptyMap())
    private var callback: IJstoreStoreUiCallback? = null
    private var displayId = Display.DEFAULT_DISPLAY

    private val deathRecipient = IBinder.DeathRecipient { mainHandler.post { detachInternal() } }

    private val binder = object : IJstoreStoreUi.Stub() {

        override fun apiVersion(): Int {
            enforceCaller()
            return JaecooConnection.STORE_UI_API_VERSION
        }

        override fun attach(
            hostToken: IBinder,
            displayId: Int,
            width: Int,
            height: Int,
            hostConfig: Bundle?,
            callback: IJstoreStoreUiCallback?
        ): Bundle {
            enforceCaller()
            val config = StoreHostConfig.fromBundle(hostConfig)
            return try {
                runOnMainThread {
                    handleAttach(hostToken, displayId, width, height, config, callback)
                }
            } catch (e: TimeoutException) {
                JstoreLog.e(TAG, "Timeout attaching the store screen", e)
                Bundle()
            } catch (e: ExecutionException) {
                JstoreLog.e(TAG, "Failed attaching the store screen", e.cause ?: e)
                Bundle()
            }
        }

        override fun relayout(width: Int, height: Int, hostConfig: Bundle?) {
            enforceCaller()
            val config = StoreHostConfig.fromBundle(hostConfig)
            mainHandler.post { handleRelayout(width, height, config) }
        }

        override fun showSection(section: String?) {
            enforceCaller()
            if (section == null) return
            mainHandler.post { screen?.select(section) }
        }

        override fun detach() {
            enforceCaller()
            mainHandler.post { detachInternal() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_BIND_STORE_UI) binder else null

    override fun onUnbind(intent: Intent?): Boolean {
        mainHandler.post { detachInternal() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        detachInternal()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun enforceCaller() {
        val uid = Binder.getCallingUid()
        if (!StoreUiCallers.isAllowedCaller(packageManager.getPackagesForUid(uid))) {
            throw SecurityException("uid $uid is not ${JaecooConnection.JAECOO_PACKAGE}")
        }
    }

    private fun <T> runOnMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return task.get(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    @Suppress("LongParameterList")
    @MainThread
    private fun handleAttach(
        hostToken: IBinder,
        displayId: Int,
        width: Int,
        height: Int,
        config: StoreHostConfig,
        callback: IJstoreStoreUiCallback?
    ): Bundle {
        detachInternal()
        this.displayId = displayId
        this.hostConfig = config
        this.callback = callback
        try {
            callback?.asBinder()?.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            JstoreLog.w(TAG, "Failed to link death recipient to the host callback", e)
        }

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display =
            displayManager.getDisplay(displayId)
                ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val themed = themedContext(display, config)

        val host = SurfaceControlViewHost(themed, display, hostToken)
        val root = FrameLayout(themed).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        fill(root, themed, config)
        host.setView(root, width, height)
        viewHost = host
        container = root
        JstoreLog.i(TAG, "Attached the store screen ${width}x$height display=$displayId")
        return Bundle().apply { putParcelable(KEY_SURFACE_PACKAGE, host.surfacePackage) }
    }

    @MainThread
    private fun handleRelayout(width: Int, height: Int, config: StoreHostConfig) {
        val host = viewHost ?: return
        val root = container ?: return
        val previous = hostConfig
        hostConfig = config
        if (config.needsReinflate(previous)) {
            val display = (
                getSystemService(
                    Context.DISPLAY_SERVICE
                ) as DisplayManager
                ).getDisplay(displayId)
            val section = screen?.currentSection()
            screen?.dispose()
            root.removeAllViews()
            fill(root, themedContext(display, config), config, section)
        }
        host.relayout(width, height)
    }

    private fun themedContext(display: Display?, config: StoreHostConfig): Context {
        val base = if (display != null) createDisplayContext(display) else this
        val configured = base.createConfigurationContext(config.applyTo(resources.configuration))
        return ContextThemeWrapper(configured, StoreScreenFactory.THEME)
    }

    private fun fill(
        root: FrameLayout,
        themed: Context,
        config: StoreHostConfig,
        section: String? = null
    ) {
        val created = StoreScreenFactory.create(
            themed,
            config,
            section ?: config.initialSection,
            hostBridge(),
            StoreScreenFactory.Deps(httpClient, play)
        )
        screen = created
        root.addView(created.view as View)
    }

    private fun hostBridge() = object : StoreScreenFactory.Host {
        override fun startActivity(intent: Intent) = safely { onStartActivity(intent) }
        override fun requestPhoneUpload() = safely { onRequestPhoneUpload() }
        override fun requestJstoreUpdate() = safely { onRequestJstoreUpdate() }
        override fun toast(message: String) = safely { onShowToast(message) }
    }

    private fun safely(call: IJstoreStoreUiCallback.() -> Unit) {
        try {
            callback?.call()
        } catch (e: RemoteException) {
            JstoreLog.w(TAG, "JaecooApp went away before the store screen could reach it", e)
        }
    }

    @MainThread
    private fun detachInternal() {
        try {
            callback?.asBinder()?.unlinkToDeath(deathRecipient, 0)
        } catch (_: NoSuchElementException) {
            // Not linked.
        }
        screen?.dispose()
        screen = null
        container?.removeAllViews()
        container = null
        viewHost?.release()
        viewHost = null
        callback = null
    }

    companion object {
        private const val TAG = "StoreUiService"
        const val ACTION_BIND_STORE_UI = "com.example.jstore.action.BIND_STORE_UI"
        private const val KEY_SURFACE_PACKAGE = "surface_package"
        private const val MAIN_THREAD_TIMEOUT_MS = 10_000L
    }
}
