# Permissions

The Permissions plugin shows every permission the app you are debugging holds, asks for one on
your behalf, and records each change as it happens — whether the user answered a system dialog or
granted a permission in the settings. An AI agent can do the same over MCP.

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

On Android, construct the plugin before the app's first activity resumes, in
`Application.onCreate` or the first activity's `onCreate`: the plugin learns which activity is in
the foreground from the next resume, and a request needs one.

## What it reports

### Android

Every permission in the app's merged manifest, with:

- **Status**: granted or denied. Runtime and install-time permissions are read with
  `checkSelfPermission`; special accesses and other app-op permissions are read as described below.
- **Category**: *Runtime* for dangerous permissions, *Special access* for permissions the user
  grants on a settings screen of their own, *Install time* for everything else.
- **Protection level** as Android defines it (`normal`, `dangerous`, `signature|appop`, …).

Special accesses are read with their own APIs, since `checkSelfPermission` does not reflect them:
display over other apps, modify system settings, exact alarms, full-screen notifications, all-files
access, usage access, installing unknown apps, the battery-optimization exemption, and the app-wide
notification switch. Any other permission with an app op is read from that op.

**Request** shows the runtime permission dialog in the app's foreground activity; for a special
access it opens that access's settings screen instead. With no activity in the foreground there is
nothing to show a dialog or a settings screen from, and the request says so. A request for
`ACCESS_FINE_LOCATION` asks for `ACCESS_COARSE_LOCATION` with it when the app declares it: some
Android versions from 12 on show an app that targets 12 or later no dialog for fine location alone,
and the request's reply says so when the app does not declare coarse location.

`ACCESS_BACKGROUND_LOCATION` can be requested only once fine or coarse location is granted; until
then Android shows nothing, so the request is refused. From Android 11, an app that targets 11 or
later gets background location on its location settings page instead of a dialog: the request
opens that page, and the note says so.

::: tip Permanently denied or never asked?
Android tells these apart only after the app asks. The note under a denied permission says which
it is when it can: *Denied once* while the dialog would still appear, *Denied permanently* once a
permission seen denied once no longer offers it, and *Never asked, or denied permanently*
otherwise. A permanently denied permission can no longer be requested; change it in the app's
settings. Background location in an app that targets Android 11 or later is the exception from
Android 11 on: Android does not report its denials, and its note says what a request opens instead.
:::

### iOS

Notifications, camera, microphone, photos, location (when in use) and contacts. iOS asks for each
permission only once, so **Request** is offered only while the status is *Not asked*; after that
the answer changes in the app's Settings page, which **Open app settings** opens.

A request for a permission whose usage description (`NSCameraUsageDescription`, …) is missing
from `Info.plist` is refused: iOS would terminate the app. App Tracking Transparency is not
reported.

### Desktop and web

Reported as unsupported: a desktop JVM app has no permission model of its own, and browser
permissions are not queried.

## Changes

The agent reads the permissions once a second while the host is connected and pushes every change
to the host, which lists them newest first. A change made in the system settings shows up the same
way as one made through the plugin. On Android a denial often leaves the status at *Denied*; a
denial that changes what a request would do is recorded all the same, with the note that says what
a request would now do.

Android ends the app's process when a permission is revoked, so a revocation is not recorded as a
change: the app reports the new state when it connects again.

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
