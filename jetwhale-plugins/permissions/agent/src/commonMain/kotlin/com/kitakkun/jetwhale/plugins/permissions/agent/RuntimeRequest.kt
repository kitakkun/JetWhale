package com.kitakkun.jetwhale.plugins.permissions.agent

private const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
private const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
private const val ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"

// Build.VERSION_CODES.R, which Android does not change.
private const val ANDROID_11 = 30

/**
 * What a request for a denied Android runtime permission would do, as the agent reports it. Kept
 * free of Android types, like everything in this file, so the decisions can be tested everywhere.
 */
internal sealed interface RequestOutlook {
    /** Whether a request brings up anything for the user to answer. */
    val requestable: Boolean
    val note: String
}

/** Background location while it is denied, in the cases where Android asks for it unlike other runtime permissions. */
internal enum class BackgroundLocation(override val requestable: Boolean, override val note: String) : RequestOutlook {
    WithoutForegroundLocation(
        requestable = false,
        note = "Android asks for background location only once fine or coarse location is granted; request one of those first.",
    ),
    OnSettingsPage(
        requestable = true,
        note = "Granted on the app's location settings page, where the user picks Allow all the time; a request opens that page.",
    ),
}

/**
 * How Android asks for the denied runtime permission [id] when it is background location and asked
 * for unlike the others, or null when [RuntimeDenial] reads it. Android shows nothing for
 * background location until fine or coarse location is granted. From Android 11, an app that
 * targets 11 or later gets it only on its location settings page, and its
 * shouldShowRequestPermissionRationale answers true whenever it is neither granted nor fixed, so
 * that answer tells nothing about a denial.
 */
internal fun backgroundLocationOf(id: String, sdkInt: Int, targetSdk: Int, isGranted: (String) -> Boolean): BackgroundLocation? = when {
    id != ACCESS_BACKGROUND_LOCATION -> null
    !isGranted(ACCESS_FINE_LOCATION) && !isGranted(ACCESS_COARSE_LOCATION) -> BackgroundLocation.WithoutForegroundLocation
    sdkInt >= ANDROID_11 && targetSdk >= ANDROID_11 -> BackgroundLocation.OnSettingsPage
    else -> null
}

/** What a request for a denied Android runtime permission does. */
internal sealed interface RuntimeRequest {
    /** @property caveat What the requester should know about what appears, or null when a dialog is expected. */
    data class Ask(val permissions: List<String>, val caveat: String?) : RuntimeRequest

    /** Asking would bring up nothing, for [reason]. */
    data class Refused(val reason: String) : RuntimeRequest
}

/**
 * The request for the denied runtime permission [id], given what a request would do for it and
 * the permissions the app [declared]. Some Android versions from 12 on show an app that targets 12
 * or later no dialog for fine location asked for without coarse location, so coarse location goes
 * with it whenever the app declares it.
 */
internal fun runtimeRequestFor(id: String, outlook: RequestOutlook, declared: Set<String>): RuntimeRequest = when {
    outlook == RuntimeDenial.Permanently -> RuntimeRequest.Refused("it is denied permanently, so Android no longer shows the dialog; change it in the app's settings")

    outlook == BackgroundLocation.WithoutForegroundLocation -> RuntimeRequest.Refused("Android shows nothing for it until fine or coarse location is granted; request one of those first")

    outlook == BackgroundLocation.OnSettingsPage -> RuntimeRequest.Ask(
        listOf(id),
        caveat = "Android opens the app's location settings page for it instead of a dialog, and may stop opening that page after the user leaves it without choosing.",
    )

    id != ACCESS_FINE_LOCATION -> RuntimeRequest.Ask(listOf(id), caveat = null)

    ACCESS_COARSE_LOCATION in declared -> RuntimeRequest.Ask(listOf(id, ACCESS_COARSE_LOCATION), caveat = null)

    else -> RuntimeRequest.Ask(
        listOf(id),
        caveat = "The app does not declare ACCESS_COARSE_LOCATION, and some Android versions from 12 on show no dialog for fine location without it.",
    )
}
