package com.kitakkun.jetwhale.host.sdk.web

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi

/**
 * Where a [JetWhaleWebView] loads its web UI from.
 *
 * Two modes so one plugin can point at a live dev server while iterating and at bundled assets once
 * shipped. Both resolve to a plain `http` origin, so `fetch()` and relative URLs behave the same in
 * development and in production.
 */
@ExperimentalJetWhaleApi
public sealed interface JetWhaleWebSource {
    /**
     * Load from a running dev server, e.g. Vite at `http://localhost:5173`. Hot-module reload keeps
     * working because the browser talks to the dev server directly. The URL is loaded as-is.
     */
    public data class DevServer(public val url: String) : JetWhaleWebSource

    /**
     * Load static assets bundled inside the plugin jar. The assets are served over a loopback HTTP
     * origin and the browser navigates to [entry].
     *
     * @param classLoader the plugin's own class loader — the one that can see the bundled assets,
     *   typically `javaClass.classLoader`. Plugin jars are loaded by isolated class loaders, so the
     *   loader that owns the assets must be passed explicitly.
     * @param resourceRoot the resource directory inside the jar holding the built web app, e.g.
     *   `"web"` for files under `src/main/resources/web/`.
     * @param entry the entry document to open, relative to [resourceRoot], e.g. `"index.html"`.
     */
    public data class BundledAsset(
        public val classLoader: ClassLoader,
        public val resourceRoot: String,
        public val entry: String,
    ) : JetWhaleWebSource
}
