/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import java.util.UUID
import java.util.concurrent.Executors

/**
 * jstore's logger for its own code: logcat as before, and warnings, errors and crashes also pushed to
 * JaecooApp's log sink (docs/log-sink-contract.md), which keeps and uploads them.
 *
 * Does nothing beyond logcat until [init] runs (`AuroraApp.onCreate`).
 */
object JstoreLog {

    private const val AUTHORITY = "com.example.jaecooapp.logsink"
    private const val METHOD = "log"
    private const val CONTRACT = "1"
    private const val RUN_CHARS = 8

    @Volatile
    private var client: LogSinkClient? = null

    /** Starts the sink client and the crash handler. Calling again does nothing. */
    @Synchronized
    fun init(context: Context, version: String, versionCode: Long) {
        if (client != null) return
        val app = context.applicationContext
        val created = LogSinkClient(
            send = { run, events -> push(app, run, events) },
            run = LogRun(
                version,
                versionCode,
                UUID.randomUUID().toString().replace("-", "").take(RUN_CHARS)
            ),
            clock = System::currentTimeMillis,
            uptime = SystemClock::elapsedRealtime,
            executor = Executors.newSingleThreadExecutor {
                Thread(it, "jstore-log").apply {
                    isDaemon =
                        true
                }
            }
        )
        client = created
        Thread.setDefaultUncaughtExceptionHandler(
            JstoreCrashHandler(Thread.getDefaultUncaughtExceptionHandler(), created::crash)
        )
        i("AppStart", "process started")
    }

    fun i(tag: String, msg: String) {
        logcat(Log.INFO, tag, msg, null)
        client?.log('I', tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        logcat(Log.WARN, tag, msg, t)
        client?.log('W', tag, msg, t?.stackTraceToString())
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        logcat(Log.ERROR, tag, msg, t)
        client?.log('E', tag, msg, t?.stackTraceToString())
    }

    /** One call to the sink: the number of events it took, or null when it cannot be reached (JaecooApp absent, too old, or gone). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun push(context: Context, run: LogRun, events: List<Map<String, Any?>>): Int? = try {
        val extras = Bundle().apply {
            putString("version", run.version)
            putLong("versionCode", run.versionCode)
            putString("run", run.run)
            putParcelableArrayList("events", ArrayList(events.map(::toBundle)))
        }
        context.contentResolver.acquireUnstableContentProviderClient(AUTHORITY)?.use {
            it.call(AUTHORITY, METHOD, CONTRACT, extras)
        }?.getInt("accepted")
    } catch (e: Exception) {
        null
    }

    private fun toBundle(event: Map<String, Any?>) = Bundle().apply {
        for ((key, value) in event) {
            when (value) {
                is String -> putString(key, value)
                is Long -> putLong(key, value)
                is Int -> putLong(key, value.toLong())
            }
        }
    }

    /** `android.util.Log` is a stub that throws in plain JVM unit tests; on a device it never throws. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun logcat(priority: Int, tag: String, msg: String, t: Throwable?) {
        try {
            Log.println(
                priority,
                tag,
                if (t !=
                    null
                ) {
                    msg + '\n' + Log.getStackTraceString(t)
                } else {
                    msg
                }
            )
        } catch (e: RuntimeException) {
            // Not on a device.
        }
    }
}

/** Gives the crash to [crash] (which writes it to the sink, synchronously), then to the handler that was installed before. */
class JstoreCrashHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val crash: (Thread, Throwable) -> Unit
) : Thread.UncaughtExceptionHandler {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            crash(thread, throwable)
        } catch (t: Throwable) {
            // Whatever went wrong while writing, the crash must still reach the previous handler.
        }
        previous?.uncaughtException(thread, throwable)
    }
}
