package com.kitakkun.jetwhale.host.sdk.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single loopback HTTP server that serves plugins' bundled web assets from inside their jars.
 *
 * Bundled assets are served over a real `http://127.0.0.1` origin rather than `file://` so that
 * `fetch()`, absolute paths and client-side routing behave exactly as they do against a dev server.
 * The server binds to loopback only and is started lazily on the first [mount].
 *
 * Each mounted web view gets its own context path (`/<id>/`), backed by the plugin's own class
 * loader, and [MountHandle.unmount] removes it when the view is disposed.
 */
internal object PluginAssetServer {
    private val nextId = AtomicInteger(0)

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also {
            // A daemon-ish cached executor keeps asset serving off the caller thread without holding
            // the JVM alive on its own.
            it.executor = null
            it.start()
        }
    }

    /**
     * Publishes [resourceRoot] (as seen by [classLoader]) under a fresh context path and returns a
     * handle whose [MountHandle.baseUrl] ends with `/`.
     */
    fun mount(classLoader: ClassLoader, resourceRoot: String): MountHandle {
        val id = nextId.getAndIncrement()
        val contextPath = "/$id/"
        val normalizedRoot = resourceRoot.trim('/')
        server.createContext(contextPath, AssetHandler(classLoader, normalizedRoot, contextPath))
        val address = server.address
        val baseUrl = "http://127.0.0.1:${address.port}$contextPath"
        return MountHandle(baseUrl) { server.removeContext(contextPath) }
    }

    internal class MountHandle(val baseUrl: String, private val onUnmount: () -> Unit) {
        fun unmount(): Unit = onUnmount()
    }

    private class AssetHandler(
        private val classLoader: ClassLoader,
        private val resourceRoot: String,
        private val contextPath: String,
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.use {
                val relative = exchange.requestURI.path.removePrefix(contextPath).trimStart('/')
                // Reject path traversal outright rather than trying to normalize it.
                if (relative.split('/').any { it == ".." }) {
                    exchange.sendResponseHeaders(403, -1)
                    return
                }
                val bytes = readAsset(relative)
                if (bytes == null) {
                    exchange.sendResponseHeaders(404, -1)
                    return
                }
                exchange.responseHeaders.add("Content-Type", contentTypeFor(relative))
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }

        private fun readAsset(relative: String): ByteArray? {
            val requested = if (relative.isEmpty()) "index.html" else relative
            resource("$resourceRoot/$requested")?.let { return it }
            // Single-page apps serve deep-link paths (no file extension) from index.html.
            if (!requested.substringAfterLast('/').contains('.')) {
                return resource("$resourceRoot/index.html")
            }
            return null
        }

        private fun resource(path: String): ByteArray? =
            classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    }
}

private fun contentTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html; charset=utf-8"
    "js", "mjs" -> "text/javascript; charset=utf-8"
    "css" -> "text/css; charset=utf-8"
    "json" -> "application/json; charset=utf-8"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "ico" -> "image/x-icon"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "ttf" -> "font/ttf"
    "map" -> "application/json; charset=utf-8"
    "wasm" -> "application/wasm"
    else -> "application/octet-stream"
}
