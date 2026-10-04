package com.kitakkun.jetwhale.plugins.permissions.agent

private const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
private const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

/**
 * The permissions an Android runtime request asks for, and what the requester should know about
 * the dialog. Kept free of Android types so the choice can be tested everywhere.
 *
 * @property caveat Why the dialog may not appear, or null when nothing is known against it.
 */
internal data class RuntimeRequest(val permissions: List<String>, val caveat: String?)

/**
 * The request for the permission [id] in an app that declares [declared]. Some Android versions
 * from 12 on show an app that targets 12 or later no dialog for fine location asked for without
 * coarse location, so coarse location goes with it whenever the app declares it.
 */
internal fun runtimeRequestFor(id: String, declared: Set<String>): RuntimeRequest = when {
    id != ACCESS_FINE_LOCATION -> RuntimeRequest(listOf(id), caveat = null)

    ACCESS_COARSE_LOCATION in declared -> RuntimeRequest(listOf(id, ACCESS_COARSE_LOCATION), caveat = null)

    else -> RuntimeRequest(
        listOf(id),
        caveat = "The app does not declare ACCESS_COARSE_LOCATION, and some Android versions from 12 on show no dialog for fine location without it.",
    )
}
