package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import java.io.InputStream
import kotlin.time.Duration

/**
 * Runs adb commands for a plugin. The host looks adb up on each call, with the same search as its own
 * port forwarding: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, the usual SDK locations and a few common
 * install directories, then `PATH`.
 *
 * Every call takes a timeout, and adb is ended when it elapses; a device that stops answering fails
 * the call instead of hanging it. A call that is cancelled ends adb too.
 */
@ExperimentalJetWhaleApi
public interface JetWhaleAdb {
    /**
     * Runs `adb <args>` to completion.
     *
     * @return adb's exit code and output. A non-zero exit is a result, not an exception: adb has
     *   run and said no.
     * @throws JetWhaleAdbUnavailableException when no adb is found or it cannot be launched.
     * @throws JetWhaleAdbTimeoutException when adb has not finished within [timeout].
     */
    public suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult

    /**
     * Runs `adb <args>` and hands its stdout to [consume], for output that is binary or unbounded,
     * such as `exec-out screencap -p` or `logcat`. Only stdout reaches [consume]: what adb prints to
     * stderr, such as a notice that it is starting its server, is kept apart. adb is ended once
     * [consume] returns, so a command that would run forever ends with the consumer.
     *
     * @return what [consume] returns.
     * @throws JetWhaleAdbUnavailableException when no adb is found or it cannot be launched.
     * @throws JetWhaleAdbTimeoutException when [consume] has not returned within [timeout]. adb is
     *   ended at that moment, which ends the stream [consume] is reading, and the call fails with
     *   this whatever [consume] then returns or throws.
     * @throws JetWhaleAdbCommandException when [consume] read stdout to its end and adb then exited
     *   with a non-zero code, so what [consume] read is not what was asked for.
     */
    public suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T
}

/**
 * A finished adb command.
 *
 * @property exitCode adb's exit code.
 * @property output What adb printed to stdout and stderr, in the order it printed it, with
 *   surrounding whitespace trimmed.
 */
@ExperimentalJetWhaleApi
public class JetWhaleAdbResult(
    public val exitCode: Int,
    public val output: String,
) {
    override fun equals(other: Any?): Boolean = other is JetWhaleAdbResult && exitCode == other.exitCode && output == other.output

    override fun hashCode(): Int = 31 * exitCode + output.hashCode()

    override fun toString(): String = "JetWhaleAdbResult(exitCode=$exitCode, output=$output)"
}

/** An adb call that ended without a result. */
@ExperimentalJetWhaleApi
public sealed class JetWhaleAdbException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * No adb was found on this machine, or it could not be launched. Retrying does not help until an
 * Android SDK is installed where the host looks for one.
 */
@ExperimentalJetWhaleApi
public class JetWhaleAdbUnavailableException(message: String, cause: Throwable?) : JetWhaleAdbException(message, cause)

/** adb did not finish within the call's timeout, and was ended. */
@ExperimentalJetWhaleApi
public class JetWhaleAdbTimeoutException(message: String, cause: Throwable?) : JetWhaleAdbException(message, cause)

/**
 * A streamed adb command that exited with a non-zero code.
 *
 * @property exitCode adb's exit code.
 * @property errorOutput What adb printed to stderr, which usually says why.
 */
@ExperimentalJetWhaleApi
public class JetWhaleAdbCommandException(
    public val exitCode: Int,
    public val errorOutput: String,
    message: String,
) : JetWhaleAdbException(message, null)
