package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.File

/**
 * A [DeviceDiscovery] over [toolPaths] with no XCTest runners, whose emulators leave their
 * discovery files in [emulatorRunningDirectories].
 */
internal fun testDeviceDiscovery(
    toolPaths: Deferred<MirrorToolPaths>,
    emulatorRunningDirectories: List<File> = emptyList(),
    iphoneScreenCapturePool: IphoneScreenCapturePool? = null,
): DeviceDiscovery {
    val grpcEndpoints = EmulatorGrpcEndpoints(emulatorRunningDirectories)
    return DeviceDiscovery(
        toolPaths = toolPaths,
        iphoneScreenCapturePool = CompletableDeferred(iphoneScreenCapturePool),
        xcTestRunners = CompletableDeferred(value = null),
        emulatorScreens = EmulatorScreens(grpcEndpoints),
        xrEmulatorClients = XrEmulatorClients(grpcEndpoints),
    )
}

/** Tool paths located already: the tools given, and none of the others. */
internal fun locatedToolPaths(adbPath: String? = null, xcrunPath: String? = null, ffmpegPath: String? = null): Deferred<MirrorToolPaths> = CompletableDeferred(MirrorToolPaths(adbPath = adbPath, xcrunPath = xcrunPath, ffmpegPath = ffmpegPath, iproxyPath = null))
