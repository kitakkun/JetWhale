package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.Duration

/** A canned adb reply, matched against the argument vector joined by spaces. */
internal class AdbRule(
    val contains: String,
    val exitCode: Int,
    val output: String,
)

internal fun reply(contains: String, output: String = "", exitCode: Int = 0): AdbRule = AdbRule(contains, exitCode, output)

/**
 * Records every argument vector a command runs and answers from a fixed list of rules, so a test
 * asserts on what reached adb rather than on what a device happened to do.
 */
internal class FakeAdb(
    private val rules: List<AdbRule> = emptyList(),
    private val streamBytes: ByteArray = ByteArray(0),
) : JetWhaleAdb {
    override val executable: String = "adb"

    val invocations = mutableListOf<List<String>>()

    /** The argument vectors run so far, each joined by spaces, for readable assertions. */
    val commands: List<String> get() = invocations.map { it.joinToString(" ") }

    override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult {
        invocations += args.toList()
        val joined = args.joinToString(" ")
        val rule = rules.firstOrNull { joined.contains(it.contains) }
        return JetWhaleAdbResult(exitCode = rule?.exitCode ?: 0, output = rule?.output.orEmpty())
    }

    override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T {
        invocations += args.toList()
        return consume(ByteArrayInputStream(streamBytes))
    }
}

/** One connected, usable emulator — the setup every tool is expected to resolve without a serial. */
internal const val ONE_DEVICE_ATTACHED =
    "List of devices attached\n" +
        "emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1\n"

internal const val TEST_SERIAL = "emulator-5554"

internal const val TEST_PACKAGE = "com.example.qa.sample"
