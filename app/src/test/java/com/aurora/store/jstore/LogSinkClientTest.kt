/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Test

class LogSinkClientTest {

    /** Runs queued work only when a test calls [runAll]. */
    private class ManualExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()
        val queued get() = queue.size

        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private class Sent(
        val version: String,
        val versionCode: Long,
        val run: String,
        val events: List<Map<String, Any?>>
    )

    private val sent = mutableListOf<Sent>()
    private val executor = ManualExecutor()
    private var reachable = true

    private fun client(): LogSinkClient = LogSinkClient(
        send = { run, events ->
            if (reachable) {
                sent.add(Sent(run.version, run.versionCode, run.run, events))
                events.size
            } else {
                null
            }
        },
        run = LogRun("1.2.1", 12L, "ab12cd34"),
        clock = { 10L },
        uptime = { 20L },
        executor = executor
    )

    private fun List<Sent>.messages() = flatMap { s -> s.events.map { it["msg"] as String } }

    @Test
    fun batchesUpToTwenty() {
        val c = client()
        repeat(25) { c.log('W', "T", "m$it") }
        executor.runAll()
        assertThat(sent.map { it.events.size }).containsExactly(20, 5).inOrder()
        assertThat(sent.messages()).isEqualTo((0 until 25).map { "m$it" })
    }

    @Test
    fun keepsEventsWhenTheSinkIsAbsent() {
        val c = client()
        reachable = false
        c.log('W', "T", "first")
        executor.runAll()
        assertThat(sent).isEmpty()
        reachable = true
        c.log('W', "T", "second")
        executor.runAll()
        assertThat(sent.messages()).containsExactly("first", "second").inOrder()
    }

    @Test
    fun anAbsentSinkNeverLogsAboutItself() {
        val c = client()
        reachable = false
        repeat(3) { c.log('W', "T", "m") }
        executor.runAll()
        reachable = true
        c.log('W', "T", "after")
        executor.runAll()
        assertThat(sent.messages().filter { it.contains("sink", ignoreCase = true) }).isEmpty()
    }

    @Test
    fun dropsOldestOverTwoHundredAndSaysSo() {
        val c = client()
        reachable = false
        repeat(205) { c.log('W', "T", "m$it") }
        executor.runAll()
        reachable = true
        c.log('W', "T", "now")
        executor.runAll()
        val first = sent.first().events.first()
        assertThat(first["level"]).isEqualTo("W")
        assertThat(first["tag"]).isEqualTo("LogSink")
        assertThat(first["msg"]).isEqualTo("6 lines dropped")
        assertThat(sent.messages()).doesNotContain("m0")
        assertThat(sent.messages()).contains("m10")
        assertThat(sent.messages()).contains("now")
    }

    @Test
    fun crashFlushesSynchronouslyAndTheLastEventIsFatalWithAStack() {
        val c = client()
        reachable = false
        c.log('W', "T", "before")
        executor.runAll()
        reachable = true
        c.crash(Thread.currentThread(), IllegalStateException("boom"))
        assertThat(executor.queued).isEqualTo(0)
        val events = sent.flatMap { it.events }
        assertThat(events.dropLast(1).map { it["msg"] }).containsExactly("before")
        val fatal = events.last()
        assertThat(fatal["level"]).isEqualTo("F")
        assertThat(fatal["tag"]).isEqualTo("Crash")
        assertThat(fatal["stack"] as String).contains("LogSinkClientTest")
    }

    @Test
    fun eventsCarryTsUptimeAndTheRunContext() {
        val c = client()
        c.log('E', "T", "m", stack = "stk")
        executor.runAll()
        val batch = sent.single()
        assertThat(batch.version).isEqualTo("1.2.1")
        assertThat(batch.versionCode).isEqualTo(12L)
        assertThat(batch.run).isEqualTo("ab12cd34")
        val e = batch.events.single()
        assertThat(e["ts"]).isEqualTo(10L)
        assertThat(e["up"]).isEqualTo(20L)
        assertThat(e["level"]).isEqualTo("E")
        assertThat(e["stack"]).isEqualTo("stk")
    }

    @Test
    fun aSendThatThrowsIsTreatedAsUnreachable() {
        val c =
            LogSinkClient({ _, _ ->
                error("binder is gone")
            }, LogRun("1", 1L, "r"), { 1L }, { 1L }, executor)
        c.log('W', "T", "m")
        executor.runAll()
        c.crash(Thread.currentThread(), RuntimeException("x"))
    }

    @Test
    fun theCrashHandlerRecordsThenDelegates() {
        var crashed: Throwable? = null
        var delegated: Throwable? = null
        val handler =
            JstoreCrashHandler(previous = { _, e -> delegated = e }, crash = { _, e ->
                crashed =
                    e
            })
        val boom = IllegalStateException("boom")
        handler.uncaughtException(Thread.currentThread(), boom)
        assertThat(crashed).isSameInstanceAs(boom)
        assertThat(delegated).isSameInstanceAs(boom)
    }

    @Test
    fun theCrashHandlerStillDelegatesWhenWritingFails() {
        var delegated = false
        val handler =
            JstoreCrashHandler(previous = { _, _ ->
                delegated = true
            }, crash = { _, _ -> throw OutOfMemoryError("while crashing") })
        handler.uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertThat(delegated).isTrue()
    }
}
