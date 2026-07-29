package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.runtime.JetWhaleSession
import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorClientPlugin
import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean

/** Device the agent's apps are reported under, so the host groups them into one two-level selector entry. */
private const val QA_DEVICE_ID = "jetwhale-qa-agent"
private const val QA_DEVICE_NAME = "QA Agent (headless)"

/**
 * One impersonated app: its own debug session, its own plugin instances and its own instrumented
 * HTTP client.
 *
 * A session on the host is one app, so nothing may be shared between apps here. Two apps registering
 * the same plugin instance would fight over its single messenger, and traffic fired through a shared
 * client would land in whichever session happened to own the capture.
 */
internal class QaApp(
    val name: String,
    val wirePluginsById: Map<String, WireLevelQaPlugin>,
    val httpClient: HttpClient,
    private val session: JetWhaleSession,
) {
    private val connected = AtomicBoolean(true)

    /**
     * Whether this app's session is still held. False once [disconnect] gave it up — which is
     * terminal, since a stopped session cannot be revived.
     */
    val isConnected: Boolean get() = connected.get()

    /** Whether every impersonated plugin could reach the host right now. Vacuously true with none registered. */
    val isReady: Boolean get() = isConnected && wirePluginsById.values.all { it.isReady }

    /**
     * Drops this app's session, so the host sees the app go away while the agent's other apps stay
     * connected. This is the point of running several: it exercises the host's disconnect handling
     * without taking the whole process down.
     *
     * Ktor serves requests in parallel, so the claim is staked with a compare-and-set: only one
     * caller of two concurrent disconnects is told it did the disconnecting.
     *
     * @return false when the session had already been given up.
     */
    fun disconnect(): Boolean {
        if (!connected.compareAndSet(true, false)) return false
        session.stop()
        httpClient.close()
        return true
    }
}

/** Connects one app and returns the handle the control API drives it through. */
internal fun startQaApp(name: String, options: QaAgentOptions): QaApp {
    val networkAgentPlugin = JetWhaleNetworkAgentPlugin()
    val wirePlugins = options.plugins.map { (id, version) -> WireLevelQaPlugin(id, version) }

    val session = startJetWhale {
        app {
            // Make this session obvious in jetwhale.listSessions so it is never mistaken for a
            // real device someone is debugging.
            appName = name
            deviceName = QA_DEVICE_NAME
            deviceId = QA_DEVICE_ID
        }
        connection {
            host = options.hostName
            port = options.hostPort
            ssl {
                trustServerCertificate()
            }
        }
        logging {
            enabled = true
            logLevel = LogLevel.INFO
            ktorLogLevel = KtorLogLevel.NONE
        }
        plugins {
            register(networkAgentPlugin)
            wirePlugins.forEach { register(it) }
        }
    }

    return QaApp(
        name = name,
        wirePluginsById = wirePlugins.associateBy { it.pluginId },
        httpClient = HttpClient {
            install(networkAgentPlugin.ktorClientPlugin())
        },
        session = session,
    )
}

internal sealed interface AppResolution {
    data class Resolved(val name: String) : AppResolution
    data class Failed(val error: String) : AppResolution
}

/**
 * Picks the app a control call is addressed to.
 *
 * [requested] may be omitted, which is the usual case: a run without `--app` has exactly one app and
 * naming it every time would be noise. With several, the call has to say which one — guessing would
 * silently drive the wrong session, and a wrong session is exactly what these runs are testing for.
 */
internal fun resolveAppName(requested: String?, known: Collection<String>): AppResolution = when {
    requested != null && requested in known -> AppResolution.Resolved(requested)
    requested != null -> AppResolution.Failed("No app named '$requested'. Started with: ${known.joinToString()}.")
    known.isEmpty() -> AppResolution.Failed("No app is running.")
    known.size == 1 -> AppResolution.Resolved(known.first())
    else -> AppResolution.Failed("Several apps are running (${known.joinToString()}); name one with \"app\".")
}

/** Why nothing addressed to an app that has been given up can arrive. */
internal fun disconnectedAppHint(appName: String): String = "App '$appName' was disconnected via /disconnect, so it holds no session. Restart the agent to get it back."

/**
 * Why a send was dropped. Every case looks identical from the caller's side — `sent: false` — but
 * only one of them is worth waiting out.
 */
internal fun sendDropHint(
    pluginId: String,
    appName: String,
    appConnected: Boolean,
    activated: Boolean,
    ready: Boolean,
): String = when {
    !appConnected -> disconnectedAppHint(appName)

    !activated ->
        "The host has not enabled '$pluginId' for app '$appName', so nothing is listening. " +
            "Enable the plugin in the host, or check the id."

    !ready -> "Connected but not prepared yet — poll /health until \"ready\": true."

    else -> "The connection was unavailable. Retry, or send with \"policy\": \"QUEUE\" to buffer it."
}
