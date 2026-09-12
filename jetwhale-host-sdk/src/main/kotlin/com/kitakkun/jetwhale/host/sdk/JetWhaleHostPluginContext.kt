package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import kotlin.time.Duration

/**
 * The host services a plugin instance is handed when it is created. It is the only way into the
 * debug tool's own capabilities — a plugin never resolves them itself, so the host stays the single
 * owner of the adb executable it resolved and of the sessions it knows about.
 */
public interface JetWhaleHostPluginContext {
    public val adb: JetWhaleAdb
    public val sessions: JetWhaleSessions
}

/**
 * Runs adb commands through the same executable the host itself uses, so a plugin never has to
 * resolve one of its own (and never disagrees with the host about which SDK is meant).
 */
public interface JetWhaleAdb {
    /** Absolute path to the adb executable, or the bare executable name when the host fell back to PATH. */
    public val executable: String

    /**
     * Runs `adb <args>` to completion and returns its exit code together with its combined
     * stdout/stderr text. A non-zero exit is a result, not an exception.
     *
     * @throws JetWhaleAdbUnavailableException when adb itself cannot be launched.
     */
    public suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult

    /**
     * Runs `adb <args>` and hands its raw stdout to [consume], for commands whose output is binary
     * or unbounded (`exec-out screencap -p`, `logcat`). The process is destroyed once [consume]
     * returns, so a long-running command ends with the consumer.
     *
     * @throws JetWhaleAdbUnavailableException when adb itself cannot be launched.
     */
    public suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T
}

/** Exit code and combined stdout/stderr text of a finished adb command. */
public class JetWhaleAdbResult(
    public val exitCode: Int,
    public val output: String,
)

/** The adb executable could not be launched — no Android SDK on this machine, or it moved. */
public class JetWhaleAdbUnavailableException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)

/** The debug sessions the host currently knows about. */
public interface JetWhaleSessions {
    /** The sessions that are connected right now; empty when no app is attached. */
    public val active: StateFlow<List<JetWhaleSessionInfo>>
}

/** One connected debug session, as a host-scoped plugin sees it. */
public class JetWhaleSessionInfo(
    public val sessionId: String,
    public val appName: String?,
    public val deviceId: String?,
    public val deviceName: String?,
    /** The pluginIds this session's agent advertised during negotiation. */
    public val installedPluginIds: List<String>,
)
