package com.kitakkun.jetwhale.plugins.deeplinks.agent

import android.app.Application
import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.content.res.XmlResourceParser
import android.os.Build
import org.xmlpull.v1.XmlPullParser
import java.io.FileNotFoundException

internal actual fun discoverDeclaredDeepLinks(): DeclaredDeepLinks {
    val context = currentApplicationOrNull()
        ?: return DeclaredDeepLinks(links = emptyList(), notes = listOf("The app's Context was not reachable, so its manifest could not be read."))
    // PackageManager exposes no intent filters (ActivityInfo carries none), so they are read from
    // the app's compiled manifest, which holds them as merged at build time.
    val manifest = openBaseManifest(context)
        ?: return DeclaredDeepLinks(links = emptyList(), notes = listOf("The app's own manifest was not among its loaded APKs, so its links could not be read."))
    val verification = appLinkVerificationStates(context)
    val links = manifest.use { parser ->
        declaredDeepLinksOf(context.packageName, manifestEvents(parser, context), apiLevel = Build.VERSION.SDK_INT, verificationOf = verification::get)
    }
    val notes = buildList {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) add("App Links verification states are reported from Android 12.")
    }
    return DeclaredDeepLinks(links = links, notes = notes)
}

/**
 * A parser of the base APK's manifest, on its root element. Opened without a cookie, the manifest is
 * looked up from the last loaded APK back, which is a split's or a shared library's (WebView's, once
 * the app has shown one) whenever the app has those. The framework and its overlays load before the
 * base APK and all have a manifest, so the first cookie without one is past the app's APKs.
 */
private fun openBaseManifest(context: Context): XmlResourceParser? {
    var cookie = 1
    while (true) {
        val parser = try {
            context.assets.openXmlResourceParser(cookie, "AndroidManifest.xml")
        } catch (_: FileNotFoundException) {
            return null
        }
        parser.nextTag()
        if (parser.getAttributeValue(null, "package") == context.packageName && parser.getAttributeValue(null, "split") == null) return parser
        parser.close()
        cookie++
    }
}

private fun manifestEvents(parser: XmlResourceParser, context: Context): Sequence<ManifestEvent> = sequence {
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> yield(ManifestEvent.Start(parser.name, attributesOf(parser, context)))
            XmlPullParser.END_TAG -> yield(ManifestEvent.End(parser.name))
            XmlPullParser.END_DOCUMENT -> return@sequence
        }
    }
}

private fun attributesOf(parser: XmlResourceParser, context: Context): Map<String, String> = (0 until parser.attributeCount).associate { index ->
    val value = parser.getAttributeValue(index)
    val resourceId = parser.getAttributeResourceValue(index, 0)
    // The compiled manifest gives a `@string/...` reference as "@<id>", not the string it names.
    val resolved = if (value.startsWith("@") && resourceId != 0) {
        runCatching { context.resources.getString(resourceId) }.getOrDefault(value)
    } else {
        value
    }
    parser.getAttributeName(index) to resolved
}

private fun appLinkVerificationStates(context: Context): Map<String, String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyMap()
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return emptyMap()
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull() ?: return emptyMap()
    return state.hostToStateMap.mapValues { (_, value) ->
        when (value) {
            DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> "verified"
            DomainVerificationUserState.DOMAIN_STATE_SELECTED -> "selected by the user"
            else -> "not verified"
        }
    }
}

/**
 * The app's [Application], reached without the app passing a Context in: the hidden
 * `ActivityThread.currentApplication()` is what the agent runtime uses for the same purpose.
 */
internal fun currentApplicationOrNull(): Context? = try {
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
} catch (_: ReflectiveOperationException) {
    null
} catch (_: SecurityException) {
    null
}
