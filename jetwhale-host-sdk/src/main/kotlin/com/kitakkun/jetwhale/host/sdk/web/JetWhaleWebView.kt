package com.kitakkun.jetwhale.host.sdk.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter

/**
 * Embeds a Chromium browser rendering a web UI (React, etc.) inside a host plugin's `Content`.
 *
 * The web UI reaches its agent counterpart through an injected `window.jetwhale` bridge:
 * - `window.jetwhale.send(type, payloadJson)` — fire-and-forget to the agent.
 * - `window.jetwhale.request(type, payloadJson)` — returns a `Promise` resolved with the agent's
 *   reply payload (rejected on failure/timeout).
 * - `window.jetwhale.onMessage((type, payloadJson) => { ... })` — receives messages the plugin
 *   forwards from the agent via [JetWhaleWebBridge.emit].
 * - A `jetwhale:ready` event fires on `window` once the bridge is installed.
 *
 * Declare the plugin with [JetWhaleWebHostPluginUi] so the host mounts this in a windowed composition.
 * The Chromium runtime is downloaded and initialized lazily the first time any web plugin is shown.
 *
 * @param messenger the plugin's own `messenger` (from `JetWhaleMessagingHostPlugin`); wires the
 *   `send`/`request` bridge calls straight to the agent.
 * @param bridge carries agent → web-UI messages; forward inbound handlers into it with
 *   [JetWhaleWebBridge.emit].
 * @param source where to load the UI from — a dev server or bundled assets.
 */
@ExperimentalJetWhaleApi
@Composable
public fun JetWhaleWebView(
    messenger: JetWhaleMessenger,
    bridge: JetWhaleWebBridge,
    source: JetWhaleWebSource,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { KcefController.ensureInitialized() }
    val status by KcefController.status.collectAsState()

    when (val current = status) {
        is KcefInitStatus.Initializing -> WebViewMessage(
            if (current.progress >= 0f) "Preparing browser… ${current.progress.toInt()}%" else "Preparing browser…",
            modifier,
        )
        is KcefInitStatus.Failed -> WebViewMessage("Failed to start browser: ${current.message}", modifier)
        KcefInitStatus.RestartRequired -> WebViewMessage("Restart the host to finish browser setup.", modifier)
        KcefInitStatus.Ready -> ReadyWebView(messenger, bridge, source, modifier)
    }
}

@Composable
private fun ReadyWebView(
    messenger: JetWhaleMessenger,
    bridge: JetWhaleWebBridge,
    source: JetWhaleWebSource,
    modifier: Modifier,
) {
    val url = rememberSourceUrl(source)
    if (url == null) {
        WebViewMessage("Loading…", modifier)
        return
    }

    val scope = rememberCoroutineScope()
    val client = remember { KCEF.newClientOrNullBlocking() }
    val browser = remember(client, url) {
        client?.createBrowser(url, CefRendering.DEFAULT, false)
    }

    if (client == null || browser == null) {
        WebViewMessage("Browser unavailable.", modifier)
        return
    }

    DisposableEffect(browser) {
        // window.cefQuery -> parse -> agent, via the plugin messenger.
        val router = CefMessageRouter.create()
        router.addHandler(BridgeQueryHandler(messenger, scope), true)
        client.addMessageRouter(router)

        // Install the window.jetwhale bridge as soon as each main-frame document finishes loading.
        val loadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    browser?.executeJavaScript(BRIDGE_SHIM_JS, browser.url ?: "", 0)
                }
            }
        }
        client.addLoadHandler(loadHandler)

        onDispose {
            client.removeMessageRouter(router)
            router.dispose()
            browser.close(true)
            client.dispose()
        }
    }

    // Agent -> web UI: forward every emitted message to window.jetwhale.onMessage listeners.
    LaunchedEffect(browser) {
        bridge.inbound.collect { message ->
            val type = Json.encodeToString(String.serializer(), message.messageType)
            val payload = Json.encodeToString(String.serializer(), message.payload)
            browser.executeJavaScript(
                "if(window.__jetwhaleReceive){window.__jetwhaleReceive($type,$payload);}",
                browser.url ?: "",
                0,
            )
        }
    }

    SwingPanel(
        factory = { browser.uiComponent },
        modifier = modifier,
    )
}

/** Resolves [source] to a loadable URL, mounting the asset server for bundled assets. */
@Composable
private fun rememberSourceUrl(source: JetWhaleWebSource): String? = when (source) {
    is JetWhaleWebSource.DevServer -> source.url
    is JetWhaleWebSource.BundledAsset -> {
        var url by remember(source) { mutableStateOf<String?>(null) }
        DisposableEffect(source) {
            val handle = PluginAssetServer.mount(source.classLoader, source.resourceRoot)
            url = handle.baseUrl + source.entry.trimStart('/')
            onDispose { handle.unmount() }
        }
        url
    }
}

@Composable
private fun WebViewMessage(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        BasicText(text = text, modifier = Modifier.wrapContentSize())
    }
}

/** A single call from `window.jetwhale` on the JS side. */
@Serializable
private data class BridgeCall(val kind: String, val type: String, val payload: String)

private class BridgeQueryHandler(
    private val messenger: JetWhaleMessenger,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : CefMessageRouterHandlerAdapter() {
    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?,
    ): Boolean {
        val raw = request ?: return false
        val call = runCatching { Json.decodeFromString(BridgeCall.serializer(), raw) }.getOrNull()
            ?: return false
        when (call.kind) {
            "send" -> {
                messenger.sendRaw(call.type, call.payload)
                callback?.success("")
            }
            "request" -> scope.launch {
                try {
                    val reply = messenger.requestRaw(call.type, call.payload, null)
                    callback?.success(reply)
                } catch (throwable: Throwable) {
                    callback?.failure(-1, throwable.message ?: "request failed")
                }
            }
            else -> return false
        }
        return true
    }
}

/**
 * Installs `window.jetwhale`. `send`/`request` go through CEF's `window.cefQuery`; `request` uses
 * cefQuery's own success/failure callbacks so a reply resolves the returned promise directly.
 */
private val BRIDGE_SHIM_JS: String =
    """
    (function () {
      if (window.jetwhale && window.jetwhale.__ready) return;
      var listeners = [];
      window.jetwhale = {
        __ready: true,
        send: function (type, payload) {
          window.cefQuery({ request: JSON.stringify({ kind: 'send', type: type, payload: payload == null ? '' : payload }) });
        },
        request: function (type, payload) {
          return new Promise(function (resolve, reject) {
            window.cefQuery({
              request: JSON.stringify({ kind: 'request', type: type, payload: payload == null ? '' : payload }),
              onSuccess: function (response) { resolve(response); },
              onFailure: function (code, message) { reject(new Error(message)); }
            });
          });
        },
        onMessage: function (cb) { listeners.push(cb); }
      };
      window.__jetwhaleReceive = function (type, payload) {
        for (var i = 0; i < listeners.length; i++) {
          try { listeners[i](type, payload); } catch (e) { console.error(e); }
        }
      };
      window.dispatchEvent(new Event('jetwhale:ready'));
    })();
    """.trimIndent()
