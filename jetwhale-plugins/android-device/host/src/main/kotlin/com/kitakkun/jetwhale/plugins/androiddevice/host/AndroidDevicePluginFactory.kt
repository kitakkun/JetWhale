package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class AndroidDevicePluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin = AndroidDevicePlugin(context.adb)
}

/**
 * Drives a connected Android device or emulator over adb, as MCP tools: find it, install and launch
 * an app, tap and type at it, look at the screen, read the log, reset its state.
 *
 * It is **host-scoped**, because a device exists whether or not an app is attached to the debug
 * tool — installing the app under test is itself one of these tools — and headless, because
 * everything it does is done by an agent rather than looked at in a window.
 *
 * Every tool runs through the host's own [JetWhaleAdb]. That is what makes the results auditable:
 * there is one adb, each tool names its device explicitly, and each result carries the argument
 * vectors it ran.
 */
@OptIn(ExperimentalJetWhaleApi::class)
private class AndroidDevicePlugin(adb: JetWhaleAdb) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        // Discovery and state
        ListDevicesCommand(adb),
        DeviceInfoCommand(adb),
        WaitForDeviceCommand(adb),
        WakeCommand(adb),
        SetRotationCommand(adb),
        SetAnimationsCommand(adb),
        // Screen
        ScreenshotCommand(adb),
        // Input
        TapCommand(adb),
        LongPressCommand(adb),
        SwipeCommand(adb),
        TypeCommand(adb),
        KeyCommand(adb),
        KeyCodeCommand(adb),
        // Apps
        InstallApkCommand(adb),
        UninstallAppCommand(adb),
        AppInfoCommand(adb),
        LaunchAppCommand(adb),
        StopAppCommand(adb),
        ClearAppDataCommand(adb),
        PermissionCommand(adb, grant = true),
        PermissionCommand(adb, grant = false),
        CurrentActivityCommand(adb),
        OpenUrlCommand(adb),
        StartActivityCommand(adb),
        // Logs
        LogcatCommand(adb),
        ClearLogcatCommand(adb),
        // Files and ports
        PushFileCommand(adb),
        PullFileCommand(adb),
        ReversePortCommand(adb),
    )
}
