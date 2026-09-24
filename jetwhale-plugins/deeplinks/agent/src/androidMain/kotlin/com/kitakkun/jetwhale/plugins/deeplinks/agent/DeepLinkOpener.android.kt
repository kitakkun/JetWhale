package com.kitakkun.jetwhale.plugins.deeplinks.agent

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = AndroidDeepLinkOpener

private object AndroidDeepLinkOpener : DeepLinkOpener {
    override val canOpen: Boolean get() = true

    override suspend fun open(url: String): DeepLinkOpenResult {
        val context = currentApplicationOrNull()
            ?: return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "the app's Context was not reachable")
        // Restricted to this app, so a link another app also claims never opens a chooser or leaves;
        // BROWSABLE, as a browser adds, so a filter the app keeps internal is not reached from here.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.name }
        if (handlers.isEmpty()) {
            return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "no browsable activity of this app handles $url")
        }
        return try {
            context.startActivity(intent)
            DeepLinkOpenResult(opened = true, handledBy = handlers, error = null)
        } catch (e: ActivityNotFoundException) {
            DeepLinkOpenResult(opened = false, handledBy = handlers, error = e.message ?: "no activity handles $url")
        }
    }
}
