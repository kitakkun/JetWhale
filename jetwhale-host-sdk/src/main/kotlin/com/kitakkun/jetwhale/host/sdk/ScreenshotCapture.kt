// The alias lives on in its original file so the `ScreenshotCaptureKt` facade class keeps existing:
// a plugin jar compiled against an earlier host-sdk calls it by that name, and moving the
// declaration would turn a rename into a NoClassDefFoundError at plugin load time.
package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal

@Deprecated(
    "Renamed to LocalIsMcpCapture: the flag is raised for jetwhale.getAccessibilityTree too, not only for screenshots.",
    ReplaceWith("LocalIsMcpCapture", "com.kitakkun.jetwhale.host.sdk.LocalIsMcpCapture"),
)
public val LocalIsScreenshotCapture: ProvidableCompositionLocal<Boolean> get() = LocalIsMcpCapture
