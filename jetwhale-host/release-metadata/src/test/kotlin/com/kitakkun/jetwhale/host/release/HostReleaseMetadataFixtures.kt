package com.kitakkun.jetwhale.host.release

import kotlin.test.assertNotNull

fun sampleHostReleaseMetadata() = HostReleaseMetadata(
    format = 1,
    version = assertNotNull(HostVersion.parse("1.0.0-alpha14")),
    mainClass = "com.kitakkun.jetwhale.host.MainKt",
    launcherContract = 1,
    runtime = HostRuntimeRequirements(javaFeatureVersion = 21, modules = listOf("java.base", "java.desktop")),
    jvmArgs = listOf("-Dcompose.application.configure.swing.globals=true"),
    platforms = mapOf(
        "macos-arm64" to HostPlatformRelease(
            url = "https://example.com/host-macos-arm64.jar",
            size = 125156159,
            sha256 = "8b2863effc431681ccf2686981887a2b01180001fc9b01f66a975d63ee3947de",
            jvmArgs = listOf("-Dapple.awt.application.appearance=system"),
        ),
        "linux-x64" to HostPlatformRelease(
            url = "https://example.com/host-linux-x64.jar",
            size = 119133124,
            sha256 = "a62592231a1d2980342640c2533f60bb6d89917816d7fa2f91373097989fbf16",
            jvmArgs = emptyList(),
        ),
    ),
)

fun HostReleaseMetadata.withMacJar(change: HostPlatformRelease.() -> HostPlatformRelease) = copy(platforms = platforms + ("macos-arm64" to platforms.getValue("macos-arm64").change()))
