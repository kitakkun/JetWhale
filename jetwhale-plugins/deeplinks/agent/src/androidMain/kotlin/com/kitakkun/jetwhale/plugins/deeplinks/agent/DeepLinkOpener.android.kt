package com.kitakkun.jetwhale.plugins.deeplinks.agent

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = AndroidDeepLinkOpener

/**
 * Opens a link as a browser would, restricted to this app. The result names the activity the platform
 * routes it to, or every candidate when several match equally and the platform lets the user choose:
 * `resolveActivity` then returns its own chooser, an activity of another package.
 */
private object AndroidDeepLinkOpener : DeepLinkOpener {
    override val canOpen: Boolean get() = true

    override suspend fun open(url: String): DeepLinkOpenResult {
        val context = currentApplicationOrNull()
            ?: return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "the app's Context was not reachable")
        // Without setPackage a link another app also claims opens a chooser; BROWSABLE is what a
        // browser adds, so a filter the app keeps internal stays unreachable from here.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val candidates = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.name }
        if (candidates.isEmpty()) {
            return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "no browsable activity of this app handles $url")
        }
        val routedTo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        val handlers = if (routedTo != null && routedTo.packageName == context.packageName) listOf(routedTo.name) else candidates
        return try {
            context.startActivity(intent)
            DeepLinkOpenResult(opened = true, handledBy = handlers, error = null)
        } catch (e: ActivityNotFoundException) {
            DeepLinkOpenResult(opened = false, handledBy = handlers, error = e.message ?: "no activity handles $url")
        }
    }
}
