# Permissions

The Permissions plugin shows every permission the app you are debugging holds, asks for one on
your behalf, and records each change as it happens — whether the user tapped a system dialog or
flipped a switch in the settings. An AI agent can do the same over MCP.

It needs no configuration beyond registering the agent.

## Setup

Install the host plugin from **Settings → Plugins**, then add the agent to your app:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-permissions-agent:<version>")
}
```

```kotlin
startJetWhale {
    plugins {
        register(JetWhalePermissionsAgentPlugin())
    }
}
```

## What it reports

### Android

Every permission in the app's merged manifest, with:

- **Status**: granted or denied, from `checkSelfPermission`.
- **Category**: *Runtime* for dangerous permissions, *Special access* for permissions the user
  grants on a settings screen of their own, *Install time* for everything else.
- **Protection level** as Android defines it (`normal`, `dangerous`, `signature|appop`, …).

Special accesses are read with their own APIs, since `checkSelfPermission` does not reflect them:
display over other apps, modify system settings, exact alarms, all-files access, usage access,
installing unknown apps, the battery-optimization exemption, and the app-wide notification switch.

**Request** shows the runtime permission dialog in the app's foreground activity; for a special
access it opens that access's settings screen instead. With no activity in the foreground there is
nothing to show a dialog in, and the request says so.

::: tip Permanently denied or never asked?
Android tells these apart only after the app asks. The note under a denied permission says which
it is when it can: *Denied once* while the dialog would still appear, *Denied permanently* once a
request made from this plugin no longer shows one, and *Never asked, or denied permanently*
otherwise.
:::

### iOS

Notifications, camera, microphone, photos, location (when in use) and contacts. iOS asks for each
permission only once, so **Request** is offered only while the status is *Not asked*; after that
the answer changes in the app's Settings page, which **Open app settings** opens.

A request for a permission whose usage description (`NSCameraUsageDescription`, …) is missing
from `Info.plist` is refused: iOS would terminate the app. App Tracking Transparency is not
reported, to keep the agent from linking a framework an app may not otherwise use.

### Desktop and web

Reported as unsupported: a desktop JVM app has no permission model of its own, and browser
permissions are not queried.

## Changes

The agent reads the permissions once a second while the plugin is enabled and pushes every change
to the host, which lists them newest first. A change made in the system settings shows up the same
way as one made through the plugin.

Revoking a permission is not possible from inside an app. Use the app's settings page, or
`adb shell pm revoke <package> <permission>` on Android.

## MCP tools

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.permissions.listPermissions` | Every permission with status, category, protection level and whether it can be requested now |
| `com.kitakkun.jetwhale.permissions.requestPermission` | Shows the permission dialog, or opens the special access's settings screen |
| `com.kitakkun.jetwhale.permissions.openAppSettings` | Opens the app's page in the system settings |

A request reports whether it was started, not what the user chose; call `listPermissions`
afterwards to see the outcome.
