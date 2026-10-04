package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbException
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.Duration

/** A canned adb reply, matched against the argument vector joined by spaces. */
@OptIn(ExperimentalJetWhaleApi::class)
internal class AdbRule(
    val contains: String,
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
    val failure: JetWhaleAdbException?,
)

@OptIn(ExperimentalJetWhaleApi::class)
internal fun reply(contains: String, output: String = "", exitCode: Int = 0, errorOutput: String = ""): AdbRule = AdbRule(contains = contains, exitCode = exitCode, output = output, errorOutput = errorOutput, failure = null)

@OptIn(ExperimentalJetWhaleApi::class)
internal fun failWith(contains: String, failure: JetWhaleAdbException): AdbRule = AdbRule(contains = contains, exitCode = 0, output = "", errorOutput = "", failure = failure)

/**
 * Records every argument vector a command runs and answers from a fixed list of rules, so a test
 * asserts on what reached adb rather than on what a device happened to do.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class FakeAdb(
    private val rules: List<AdbRule> = emptyList(),
    private val streamBytes: ByteArray = ByteArray(0),
) : JetWhaleAdb {
    val invocations = mutableListOf<List<String>>()

    /** The timeout each call was given, in the order of [invocations]. */
    val timeouts = mutableListOf<Duration>()

    /** The argument vectors run so far, each joined by spaces, for readable assertions. */
    val commands: List<String> get() = invocations.map { it.joinToString(" ") }

    override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult {
        val rule = record(args, timeout)
        rule?.failure?.let { throw it }
        return JetWhaleAdbResult(exitCode = rule?.exitCode ?: 0, output = rule?.output?.trim().orEmpty(), errorOutput = rule?.errorOutput?.trim().orEmpty())
    }

    override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T {
        record(args, timeout)?.failure?.let { throw it }
        return consume(ByteArrayInputStream(streamBytes))
    }

    private fun record(args: Array<out String>, timeout: Duration): AdbRule? {
        invocations += args.toList()
        timeouts += timeout
        val joined = args.joinToString(" ")
        return rules.firstOrNull { joined.contains(it.contains) }
    }
}

/** One connected, usable emulator — the setup every tool is expected to resolve without a serial. */
internal const val ONE_DEVICE_ATTACHED =
    "List of devices attached\n" +
        "emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1\n"

internal const val TEST_SERIAL = "emulator-5554"

internal const val TEST_PACKAGE = "com.example.qa.sample"
