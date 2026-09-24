package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

internal actual fun platformPermissionSource(): PermissionSource = IosPermissionSource()

/**
 * The privacy permissions of the frameworks an app most often asks for. App Tracking Transparency
 * is left out: linking it into an app that does not use it invites App Store review questions, for
 * a status that is rarely what a debugging session is about.
 */
private class IosPermissionSource : PermissionSource {
    override val platform: String get() = "iOS"
    override val unsupportedReason: String? get() = null

    // CLLocationManager drops a pending authorization request when it is deallocated.
    private var locationManager: CLLocationManager? = null

    private val permissions: List<IosPermission> = listOf(
        IosPermission(
            id = "ios.notifications",
            label = "Notifications",
            usageKey = null,
            read = ::notificationStatus,
            request = {
                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { _, _ -> }
            },
        ),
        IosPermission(
            id = "ios.camera",
            label = "Camera",
            usageKey = "NSCameraUsageDescription",
            read = { captureStatus(AVMediaTypeVideo) },
            request = { AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ -> } },
        ),
        IosPermission(
            id = "ios.microphone",
            label = "Microphone",
            usageKey = "NSMicrophoneUsageDescription",
            read = { captureStatus(AVMediaTypeAudio) },
            request = { AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { _ -> } },
        ),
        IosPermission(
            id = "ios.photos",
            label = "Photos",
            usageKey = "NSPhotoLibraryUsageDescription",
            read = ::photosStatus,
            request = { PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { _ -> } },
        ),
        IosPermission(
            id = "ios.location",
            label = "Location (when in use)",
            usageKey = "NSLocationWhenInUseUsageDescription",
            read = ::locationStatus,
            request = { (locationManager ?: CLLocationManager().also { locationManager = it }).requestWhenInUseAuthorization() },
        ),
        IosPermission(
            id = "ios.contacts",
            label = "Contacts",
            usageKey = "NSContactsUsageDescription",
            read = ::contactsStatus,
            request = { CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { _, _ -> } },
        ),
    )

    override suspend fun read(): List<PermissionState> = permissions.map { permission ->
        val status = permission.read()
        val missingKey = permission.usageKey?.takeUnless(::hasInfoPlistKey)
        PermissionState(
            id = permission.id,
            label = permission.label,
            category = PermissionCategory.Runtime,
            protection = null,
            status = status,
            // iOS shows its dialog only once; after that the answer changes in Settings alone.
            requestable = status == PermissionStatus.NotDetermined && missingKey == null,
            note = when {
                missingKey != null -> "Info.plist has no $missingKey, so iOS would terminate the app on a request."
                status == PermissionStatus.NotDetermined -> null
                else -> "iOS asks only once; change it in the app's Settings page."
            },
        )
    }

    override suspend fun request(id: String): PermissionActionResult {
        val permission = permissions.firstOrNull { it.id == id } ?: return failure("no permission is reported as $id")
        permission.usageKey?.takeUnless(::hasInfoPlistKey)?.let { key ->
            return failure("Info.plist has no $key; iOS terminates an app that asks without one")
        }
        if (permission.read() != PermissionStatus.NotDetermined) {
            return failure("iOS has already asked for ${permission.label}; change it in the app's Settings page")
        }
        withContext(Dispatchers.Main) { permission.request() }
        return PermissionActionResult(message = "Asked for ${permission.label}; the user's choice arrives as a change.", error = null)
    }

    override suspend fun openAppSettings(): PermissionActionResult = withContext(Dispatchers.Main) {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return@withContext failure("the Settings URL is unavailable")
        val opened = suspendCancellableCoroutine { continuation ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>()) { success -> continuation.resume(success) }
        }
        if (opened) PermissionActionResult(message = "Opened the app's Settings page.", error = null) else failure("iOS did not open the Settings page")
    }
}

/**
 * @param usageKey The Info.plist usage-description key iOS requires before it shows the dialog;
 *   null when none is.
 * @param request Starts the system request; runs on the main thread.
 */
private class IosPermission(
    val id: String,
    val label: String,
    val usageKey: String?,
    val read: suspend () -> PermissionStatus,
    val request: () -> Unit,
)

private fun failure(error: String) = PermissionActionResult(message = "", error = error)

private fun hasInfoPlistKey(key: String): Boolean = NSBundle.mainBundle.objectForInfoDictionaryKey(key) != null

private suspend fun notificationStatus(): PermissionStatus = suspendCancellableCoroutine { continuation ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        continuation.resume(
            when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized -> PermissionStatus.Granted
                UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> PermissionStatus.Limited
                UNAuthorizationStatusDenied -> PermissionStatus.Denied
                UNAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
                else -> PermissionStatus.Denied
            },
        )
    }
}

private fun captureStatus(mediaType: String?): PermissionStatus = when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
    AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
    AVAuthorizationStatusDenied -> PermissionStatus.Denied
    AVAuthorizationStatusRestricted -> PermissionStatus.Restricted
    AVAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
    else -> PermissionStatus.Denied
}

private suspend fun photosStatus(): PermissionStatus = when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)) {
    PHAuthorizationStatusAuthorized -> PermissionStatus.Granted
    PHAuthorizationStatusLimited -> PermissionStatus.Limited
    PHAuthorizationStatusDenied -> PermissionStatus.Denied
    PHAuthorizationStatusRestricted -> PermissionStatus.Restricted
    PHAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
    else -> PermissionStatus.Denied
}

@Suppress("DEPRECATION") // The class-level query needs no manager instance and is still answered on every iOS version.
private suspend fun locationStatus(): PermissionStatus = when (CLLocationManager.authorizationStatus()) {
    kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.Granted
    kCLAuthorizationStatusDenied -> PermissionStatus.Denied
    kCLAuthorizationStatusRestricted -> PermissionStatus.Restricted
    kCLAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
    else -> PermissionStatus.Denied
}

private suspend fun contactsStatus(): PermissionStatus = when (CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)) {
    CNAuthorizationStatusAuthorized -> PermissionStatus.Granted

    CNAuthorizationStatusDenied -> PermissionStatus.Denied

    CNAuthorizationStatusRestricted -> PermissionStatus.Restricted

    CNAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined

    // iOS 18's limited access, which older SDK bindings do not name.
    else -> PermissionStatus.Limited
}
