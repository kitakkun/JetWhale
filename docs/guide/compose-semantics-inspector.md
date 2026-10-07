# Compose Semantics Inspector

The Compose Semantics Inspector reads the **semantics tree of your running app** — every node, its
labels, its bounds and the actions it exposes — and lets you browse it in the host and hand it to an
AI agent over [MCP](/guide/mcp-server). On Android and desktop that is the Compose tree, with the
Android `View`s around it; on iOS it is the accessibility tree, which carries UIKit, SwiftUI and
Compose content alike. A capture takes about 14 ms, so an agent can read the screen between every
action instead of guessing at pixels.

**Works with:** Android, desktop (JVM) and iOS. Not the web; see [Limits](#limits).

## Setup

### Install the host plugin

Install **Compose Semantics Inspector** from **Settings → Plugins → Add Plugins → Official
Plugins**. To install it by Maven coordinates or from a file, see
[Host Settings → Plugins](/guide/host-settings#plugins).

### Add the agent to your app

One artifact carries both the plugin and the probes:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-compose-semantics-inspector-agent:<version>")
}
```

```kotlin
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsAgentPlugin

startJetWhale {
    connection { /* ... */ }
    plugins {
        register(JetWhaleSemanticsAgentPlugin())
    }
}
```

This is common code. Without a probe the plugin still answers, with an empty tree and a warning the
host shows.

### Install a probe

A probe finds the app's roots for the plugin to read:

::: code-group

```kotlin [Android]
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installJetWhaleSemanticsProbe(this)
        startJetWhale { /* … */ }
    }
}
```

```kotlin [Android or desktop, in a composition]
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsProbe

setContent {           // on desktop: inside Window { }, under @OptIn(ExperimentalComposeUiApi::class)
    JetWhaleSemanticsProbe()
    App()
}
```

```kotlin [iOS]
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe

installJetWhaleSemanticsProbe()   // once at startup, on the main thread
```

:::

| Target | What the probe covers |
|---|---|
| **Android**, from the `Application` (recommended) | Installed in `onCreate()`, before any activity exists, it hooks the callback Compose fires when it creates the view backing a composition, so it sees **every** window holding one, a `Dialog`'s or `Popup`'s included. Installed later, it also scans the resumed activity's window, but a window already open and never resumed again can be missed. The install is process-wide and idempotent, and closing the returned handle restores the previous callback. |
| **Android**, in a composition | Only the window that composition lives in, for as long as it stays composed. A `Dialog` or `Popup` is a window of its own and needs its own call, or the `Application` probe. |
| **Desktop (JVM)** | The window it is called in, through `ComposeWindow.semanticsOwners`, so dialogs and popups inside it appear and disappear by themselves. A second `Window { }` needs its own call. The API is `@ExperimentalComposeUiApi`, so opt in at the call site or the module does not compile. |
| **iOS** | Every window the app has, following `UIWindowDidBecomeVisible` / `UIWindowDidBecomeHidden` for later ones, through the accessibility protocol. Call it before or after `startJetWhale`. A pure Swift app cannot call it yet: the call has to sit in Kotlin the app links, as in the demo's `cmpAppViewController()`. |

The two Android probes can be combined: registrations are counted per window, so neither pulls a
window out from under the other.

::: warning Debug builds only
The probe makes your app's UI structure readable, and actions make it drivable, over the JetWhale
connection. Wire both up in debug builds only, as you would the rest of JetWhale.
:::

### Application accessibility

On iOS the tree is there to read only while **application accessibility** is on: the setting that
VoiceOver, the other assistive features and Xcode's Accessibility Inspector turn on, and that UIKit
checks before loading its accessibility support into an app. While it is off, a capture comes back
empty.

- **On a simulator** the probe turns it on when you install it, and the running app picks it up at
  once. The setting belongs to the simulator, so it stays on for every app there after yours exits.
  The call is private API, compiled into the simulator slice only.
- **On a device** the probe changes nothing. The tree should be complete while VoiceOver or another
  assistive feature is on, or while the Accessibility Inspector inspects the device. That is how
  UIKit decides on a simulator; it has not been confirmed on a device yet.

::: details Leaving the simulator's setting alone
Install the probe with `installJetWhaleSemanticsProbe(enableSimulatorApplicationAccessibility = false)`
and turn the setting on yourself when you want a tree, then relaunch the app, which reads it at
startup:

```bash
xcrun simctl spawn booted defaults write com.apple.Accessibility ApplicationAccessibilityEnabled -bool true
```

To turn it off again, delete it, along with `AccessibilityEnabled`, which the probe turns on with it,
and relaunch:

```bash
xcrun simctl spawn booted defaults delete com.apple.Accessibility ApplicationAccessibilityEnabled
xcrun simctl spawn booted defaults delete com.apple.Accessibility AccessibilityEnabled
```

With several simulators booted, put the simulator's UDID in place of `booted`.
:::

## Using it

### Browsing the tree

Select your app, open **Compose Semantics Inspector**, and press **Refresh**.

- **Auto** captures again once a second. It is off by default: a capture reads the app's semantics
  on its main thread, so leaving it on makes the app do that work forever.
- **Merged** folds a control's label into its clickable node, as accessibility services see it;
  turn it off to see every node separately.
- **Interactive only** keeps the nodes that expose an action, are editable or scroll, plus their
  ancestors.
- **Include invisible** adds nodes that are not laid out or are fully clipped away.
- The **search box** matches text, `contentDescription`, `testTag`, role and id.

Select a node to see its full semantics, with a button for every action it exposes, and **Copy
`adb shell input tap`** (**Copy `idb ui tap`** for an iOS node) for when you do want the input
system. A row marked **not operable** offers something to do that a user could not do right now,
because the node is disabled or a touch would not reach it; see
[Reachability](/reference/semantics-tree#reachability). The host also shows what the capture cost on
the device and the round trip.

### Editing View attributes

Select an Android `View` node and its platform attributes appear under its semantics, grouped into
State, Layout, Appearance, Text and Info — padding, a color, `visibility`, a size — and most can be
changed live, so you can try a change without a rebuild. The full list is in the
[reference](/reference/semantics-tree#view-attributes).

- **An edit is temporary.** The app owns the property: a relayout, a rebind or the app writing it
  takes the value back. It is a way to see a change, not to make one.
- **Compose nodes have none.** A semantics node is a projection of composition state, so the next
  recomposition would undo a write, which is why Android Studio's Layout Inspector does not edit
  Compose either.

### Highlighting on the device

Turn on **Highlight** and the app draws a translucent box over the selected node; hovering a row
shows that node instead while the pointer is on it. It works for Compose and `View` nodes alike, and
a node in a dialog is highlighted in the dialog's own window.

- **It is off by default, on purpose.** The box is drawn into the app, so anything that takes a
  screenshot of the device while it is up captures it too — a `screencap`, the
  [Device Mirror](/guide/device-mirror), a QA run. Turn it off before you capture.
- **It never appears in the captured tree**, since it is a window overlay drawn after the root
  view's children.
- **It follows the node, or goes away**, as the window redraws, scrolls or relayouts; a node
  scrolled out of a lazy list gets its box back when it returns while still selected.
- **It clears itself.** The app drops a highlight it has not heard about for 30 seconds, so a host
  that crashes cannot leave a box on the screen. Closing the inspector, disabling the plugin and
  disconnecting clear it at once.

There is deliberately no MCP tool for this: an agent reads a node's bounds from the tree already, and
a highlight would only put a box into its screenshots.

## MCP tools

With the [MCP server](/guide/mcp-server) running, an AI agent can read the tree and act on it through
these tools. Each takes the `sessionId` of the app's session. Their arguments and output formats are
in the [Semantics Tree reference](/reference/semantics-tree#mcp-tools).

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.semantics.findNodes` | Matching nodes as a flat list, each with its `rootId` / `id`, bounds and a ready-made `tap` point; with no criteria, everything interactive on screen |
| `com.kitakkun.jetwhale.semantics.getNodeTree` | The whole tree, structure included, as JSON or a compact text outline |
| `com.kitakkun.jetwhale.semantics.nodeAt` | Which node a tap at a screen point would reach |
| `com.kitakkun.jetwhale.semantics.performNodeAction` | Runs a node's own action — click, long click, set text, scroll, focus, dismiss, bring into view |
| `com.kitakkun.jetwhale.semantics.getViewAttributes` | An Android `View` node's platform attributes |
| `com.kitakkun.jetwhale.semantics.setViewAttribute` | Changes one of them, temporarily |

A typical loop finds a node, acts on it, and looks again:

```
findNodes(testTag: "login-button")     → { "nodes": [{ "rootId": "compose-root-1f2e", "id": 42, … }] }
performNodeAction(nodeId: 42, action: "Click")
findNodes()                            → the new screen's interactive nodes
```

`performNodeAction` runs the node's own action, so it needs no coordinates and cannot land on
whatever moved into that spot; prefer it over `adb shell input tap`. Bounds and tap points are in the
node's `unit`: pixels on Android and desktop, points on iOS.

## Limits

- **No web probe.** `ComposeViewport` exposes no owner for the agent to read; a custom scene can still
  be registered by hand — see [Custom scenes](/reference/semantics-tree#custom-scenes-and-threading).
- **A window with no Compose in it is not captured**, such as a plain `AlertDialog` built from views:
  this plugin inspects Compose apps, not views in general.
- **A merged capture can fold an `AndroidView` away** with the node an ancestor merges; capture
  unmerged to see the views under it.
- **On iOS**, a Compose element cannot take `SetText`, `InsertText`, `ImeAction`, `RequestFocus` or
  `ScrollToIndex`, `LongClick` is not available, and a Compose `role` is folded into label and
  traits; see [iOS nodes](/reference/semantics-tree#ios-nodes).
- **A secure field's contents are never captured**: a password field reads as editable, with no
  text.

## Troubleshooting

- **"No root is registered."** No probe is installed. Add one as in
  [Install a probe](#install-a-probe). On JS and Wasm this is expected.
- **On iOS the tree is empty, and `includeInvisible` shows only the window and a few views with empty
  bounds.** Application accessibility is off: on a device where nothing turned it on, or on a
  simulator where the probe was installed with `enableSimulatorApplicationAccessibility = false`. See
  [Application accessibility](#application-accessibility).
- **A dialog's contents are missing.** On Android a dialog is a separate window: the `Application`
  probe finds it, an in-composition probe registers only its own window. On iOS a dialog stays inside
  the window that showed it.
- **An action comes back `performed: false`.** The message says why: the node does not expose that
  action, it is disabled, or its handler declined. Capture again and check the node's `actions`.
- **Node ids changed between calls.** A node's id is stable while it stays composed (a `View`
  node's, while the view is alive), and ids are unique only within their root. After anything that
  recomposes the screen, capture again rather than reusing ids.
