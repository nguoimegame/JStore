/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui.store

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aurora.store.R

/**
 * The confirmation the store screen needs before it removes an app. A window cannot be opened
 * from inside the embedded surface, so JaecooApp is asked to start this transparent activity
 * (onStartActivity) and the answer comes back through [StoreDialogResults].
 */
class StoreDialogActivity : AppCompatActivity() {

    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        if (intent.getStringExtra(EXTRA_KIND) != KIND_CONFIRM_UNINSTALL ||
            packageName.isNullOrEmpty()
        ) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.jstore_store_uninstall_confirm_title)
            .setMessage(getString(R.string.jstore_store_uninstall_confirm_message, label))
            .setPositiveButton(R.string.jstore_store_uninstall_confirm_ok) { _, _ ->
                answer(packageName, true)
            }
            .setNegativeButton(R.string.jstore_store_uninstall_cancel) { _, _ ->
                answer(packageName, false)
            }
            .setOnDismissListener {
                if (!answered) StoreDialogResults.deliver(packageName, false)
                finish()
            }
            .show()
    }

    private fun answer(packageName: String, confirmed: Boolean) {
        answered = true
        StoreDialogResults.deliver(packageName, confirmed)
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
        const val KIND_CONFIRM_UNINSTALL = "confirm_uninstall"

        fun confirmUninstallIntent(context: Context, packageName: String, label: String): Intent =
            Intent(context, StoreDialogActivity::class.java)
                .putExtra(EXTRA_KIND, KIND_CONFIRM_UNINSTALL)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Where [StoreDialogActivity] reports to: the screen that asked, in the same process. */
object StoreDialogResults {

    private var listener: ((packageName: String, confirmed: Boolean) -> Unit)? = null

    /** Replaces any earlier listener: only one confirmation is ever open. */
    fun listen(onResult: (packageName: String, confirmed: Boolean) -> Unit) {
        listener = onResult
    }

    fun stopListening() {
        listener = null
    }

    fun deliver(packageName: String, confirmed: Boolean) {
        listener?.invoke(packageName, confirmed)
    }
}
