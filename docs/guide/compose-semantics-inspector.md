# Compose Semantics Inspector

The Compose Semantics Inspector is an official JetWhale plugin that reads the **Compose node tree of your
running app** — every node, its labels, its bounds, and the actions it exposes — and lets you both
browse it in the host and hand it to an AI agent over [MCP](/guide/mcp-server).

- ⚡ **~14 ms** per capture end-to-end, against ~2.7 s for `android layout` — see
  [Why not the CLI?](#why-not-the-cli)
- 🌲 Live tree of the app's Compose nodes, with search and an *interactive only* filter
- 🎯 Per-node detail: role, text, `contentDescription`, `testTag`, state, bounds in root **and**
  screen pixels
- 👆 Run a node's own semantics action — click, long click, set text, scroll, focus, dismiss —
  from the host or from an AI agent
- 🪟 Dialogs and popups appear as their own roots, because that is what they are in Compose
- 🤝 On Android, the Android `View`s around and inside the composition are in the same tree, and a
  `View`'s attributes can be read and edited live — see [Android View support](#android-view-support)
- 🖐 Whether a tap aimed at a node would actually arrive, worked out from the capture rather than by
  tapping — see [Can a finger reach it?](#can-a-finger-reach-it)
- 🤖 Six MCP tools so an agent can see the screen structurally instead of guessing at pixels

## What the tree contains

The tree is the Compose **semantics** tree: the same tree an accessibility service sees, and the one
that says what is actually clickable. A `Box` that only lays out pixels does not appear on its own;
a `Button` does, carrying its label and its `OnClick` action.

Two views of it are available, switchable in the host and per MCP call:

| | What you get |
|---|---|
| **Merged** (default) | A `Button`'s label is folded into the clickable node — one node per control. This is what accessibility services and `performClick` see, and usually what you want. |
| **Unmerged** | Every semantics node stays separate, closer to how the UI is written. Useful when you need to see exactly which composable contributed which property. |

## Android View support

Very little Compose is *only* Compose. A screen usually sits in an Activity's or a Fragment's layout,
and an `AndroidView { }` puts a `View` back inside the composition. On Android the capture follows
both crossings, so a root is **one window**, not one composition:

```
MainActivity                              ← the window's decor view
└─ … the layout around the ComposeView …
   └─ AndroidComposeView                   ← where the composition starts
      └─ Column                            ← Compose semantics from here down
         ├─ Button · Send
         └─ LinearLayout                   ← an AndroidView { }, and its subtree
            ├─ TextView · @id/status
            └─ Button · @id/submit
```

A `View` node is its own node type on the wire — `"type": "view"`, against `"compose"` for a
semantics node — and it fills the same fields a Compose node does: `text`, `contentDescription`,
`bounds`, `actions`, and the `enabled`/`clickable`/`editable`/`scrollable` flags. So nothing that
only reads the tree has to special-case it. What is particular to it:

| | `View` node |
|---|---|
| `id` | **negative**, assigned by the agent and valid while the view is alive — Compose's semantics ids are non-negative, so the two can never collide |
| `viewClass` | the view's class, e.g. `android.widget.Button` |
| `resourceId` | the entry name of its `android:id`, e.g. `submit` for `@id/submit` — a `View` has no `testTag`, and this is what plays that role |

A Compose node carries `role`, `testTag` and `stateDescription`, which a `View` has no counterpart
for; a `View` node carries `viewClass` and `resourceId`, which a Compose node has no counterpart for.

The two node types are told apart on the wire by `"type": "compose"` / `"type": "view"`, so host and
agent have to be built against the same protocol version — a mismatch fails to decode rather than
degrading.

`performNodeAction` works on a `View` node too, running the view's own API rather than a synthesised
tap: `Click` → `performClick()`, `LongClick` → `performLongClick()`, `SetText` / `InsertText` on an
`EditText`, `ImeAction` → `onEditorAction`, `ScrollBy` → `scrollBy`, `RequestFocus` →
`requestFocus()`. `Dismiss`, `Expand` and `Collapse` have no `View` counterpart and come back
`performed: false` saying so. As always, only what a node lists in `actions` can be invoked.

### Editing View attributes

A `View` node also exposes its **platform attributes** — the properties a Layout Inspector shows —
and most of them can be changed live, so you can try a padding, a color or a `GONE` without a
rebuild. Select a `View` node in the host and the attributes appear under its semantics, grouped:

| Group | Attributes |
|---|---|
| **State** | `visibility` (`VISIBLE`/`INVISIBLE`/`GONE`), `enabled`, `selected`, `activated`, `clickable`, `focusable`, and `focused` read-only |
| **Layout** | `layout.width` / `layout.height` (`MATCH_PARENT`, `WRAP_CONTENT` or a pixel length), `padding.*`, `margin.*` (when the parent hands out margins), `minWidth` / `minHeight`, and `bounds` read-only |
| **Appearance** | `alpha`, `backgroundColor` (when the background is a flat color; otherwise `background` names the drawable, read-only), `elevation`, `translationX` / `translationY`, `rotation`, `scaleX` / `scaleY` |
| **Text** | on a `TextView`: `text`, `hint`, `textSize`, `textColor`, `maxLines` |
| **Info** | `id` (`@id/name`) and `class`, both read-only |

Each attribute has one type, and the type is what decides the editor — a `visibility` is always a
dropdown of its options, a `padding.left` always a pixel field. `layout.width` and `layout.height`
take *either* a constant or a length, so they have a type of their own that says both: a dropdown of
`MATCH_PARENT`, `WRAP_CONTENT` and **Fixed**, with a pixel field that Fixed enables. The editor looks
the same whichever the size currently is, so a `WRAP_CONTENT` can be given a width and a width can be
put back to `WRAP_CONTENT`.

Three things to know:

- **It is a curated list, not reflection.** Every attribute is named explicitly. Reflection over a
  view's getters is what makes the equivalent elsewhere fragile, and Android's non-SDK interface
  restrictions block most of what it would reach anyway. The list says exactly what the agent will
  touch.
- **An edit is temporary.** The app owns the property: a relayout, a rebind, or the app writing it
  itself takes the value back. This is a way to *see* a change, not to make one.
- **Compose nodes have none.** A semantics node is a projection of composition state, so writing to
  it would be undone by the next recomposition — which is why Android Studio's Layout Inspector
  edits views and not Compose either. Asking for the attributes of a Compose node answers a message
  saying so rather than failing.

Attributes are fetched per node when you select one, never as part of a capture: a tree of two
hundred nodes must not carry thirty attributes each.

Two MCP tools do the same from an agent —
[`getViewAttributes`](#com-kitakkun-jetwhale-semantics-getviewattributes) and
[`setViewAttribute`](#com-kitakkun-jetwhale-semantics-setviewattribute).

Two limits are worth knowing:

- **A window with no Compose in it is not captured.** The composition is what announces a window to
  the probe, so a plain `AlertDialog` built from views does not appear. This plugin inspects Compose
  apps; it is not a general View inspector.
- **A merged capture can fold an `AndroidView` away.** The embedded views hang off the semantics
  node the `AndroidView { }` creates; when an ancestor merges its descendants (a `Button`, a
  `mergeDescendants = true` modifier), that node is folded into the ancestor in the merged tree and
  the views under it go with it. Capture unmerged (`merged: false`) to see them.

Other platforms are unaffected: desktop reads a composition through its `SemanticsOwner`, and its
roots stay one-per-composition.

## Setup

### Install the host plugin

The Compose Semantics Inspector is in the host's **official catalog**: open **Settings → Plugins →
Add Plugins → Official Plugins** and install it with one click — no coordinates needed. See
[Host Settings → Plugins](/guide/host-settings#plugins) for the other install routes.

### Add the agent to your app

Add the agent to the app being debugged — one artifact, carrying both the plugin and the probes
(see [Platform support](#platform-support) for which targets have a probe):

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-compose-semantics-inspector-agent:<version>")
}
```

Register the plugin with the agent runtime, and install a probe so it has roots to read.

### Registering the plugin

```kotlin
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsAgentPlugin

startJetWhale {
    connection {
        endpoints {
            ws("localhost", 5080)
        }
    }
    plugins {
        register(JetWhaleSemanticsAgentPlugin())
    }
}
```

This part is common code — it compiles on every Compose Multiplatform target. Without a probe the
plugin still answers, reporting an empty tree and a warning saying so, which the host shows.

### Installing a probe

There are two ways in, and they can be combined — registrations are reference counted per window, so
neither can pull a window out from under the other.

#### From the Application layer (recommended)

```kotlin
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installJetWhaleSemanticsProbe(this)
        startJetWhale { /* … */ }
    }
}
```

Installed from `onCreate()`, before any activity exists, it hooks the callback Compose fires when it
creates the view backing a composition — so it sees **every** window holding one, including the
separate one a `Dialog` or a `Popup` opens. No screen has to change.

Installed later it also scans the resumed activity's window, so roots created before the call are
not lost — but roots in windows that were already open and are never re-resumed can be.

The install is process-wide and idempotent, and closing the returned handle restores whatever
callback was there before — so it composes with the Compose test framework rather than displacing
it.

#### From inside the composition

```kotlin
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsProbe

setContent {
    JetWhaleSemanticsProbe()
    App()
}
```

Use this when the Application layer is not yours to touch, or when only one screen should be
readable. It registers **its own** root for as long as it stays composed — on Android, the window
that root lives in. A `Dialog` or `Popup` is a root of its own, so add a call inside those too, or
install the Application-level probe, which finds them all.

::: warning Debug builds only
The probe makes your app's UI structure readable, and the actions below make it drivable, over the
JetWhale connection. Wire both up in debug builds only, exactly as you would the rest of JetWhale.
:::

## Using the host UI

Open the **Compose Semantics Inspector** in the host, select your app's session, and press
**Refresh**.

- **Auto** re-captures once a second. It is off by default: a capture reads the app's semantics on
  its main thread, so leaving it on makes the app do that work forever.
- **Interactive only** keeps the nodes that expose an action, are editable, or scroll — plus their
  ancestors, so the structure stays readable.
- **Include invisible** adds nodes that are not laid out or are fully clipped away.
- The **search box** matches text, `contentDescription`, `testTag`, role and id.

Select a node to see its full semantics on the right, along with a button for every action it
actually exposes. There is also a **Copy `adb shell input tap`** button for the times you do want to
drive the app through the input system. Select an Android `View` node and its editable attributes
appear below that — see [Editing View attributes](#editing-view-attributes).

## Can a finger reach it?

Every captured node says whether a gesture aimed at it would actually arrive. A node that accepts
touch input but cannot receive one is marked `"hittable": false`, with `obscuredBy` naming what takes
the touch instead, and the tree view tags the row **unreachable**.

It is worked out from the capture alone — no tap sent, nothing to wait for — by walking the windows
from the top down and, within a window, the last-drawn node first, the way the platform dispatches a
touch. That catches what is worth catching:

| The button is… | Reported as |
| --- | --- |
| under a dialog or a popup | `hittable: false`, `obscuredBy` the node on top |
| behind a modal window, anywhere on screen | `hittable: false`, `obscuredBy` that window's root |
| scrolled out of its container | `hittable: false`, no `obscuredBy` — no area to aim at |
| under a later sibling that takes touches | `hittable: false`, `obscuredBy` that sibling |
| a clickable row whose center is its own button | `hittable: false`, `obscuredBy` that button — the tap is consumed there |
| a list whose center is one of its own rows | `hittable: true` — a drag starting on the row still scrolls the list |

The last two differ because the gesture does: a tap stops at the deepest node that takes it, while
a drag is seen by every scrollable on the way down. So `obscuredBy` on a scrollable only ever names
something outside it — a sibling drawn on top, or another window. A node that accepts both — a
scrollable that is also clickable — reads `hittable: true` when either gesture gets through.

Two things a capture cannot see, and both make it *optimistic* — a node can read as reachable that a
finger would not reach:

- an overlay that consumes touches without exposing any semantics (a bare `pointerInput`, an
  `OnTouchListener`),
- a gesture an ancestor swallows before the node sees it.

On the Compose side the order this relies on is guaranteed — semantics children come from the
layout's z-sorted children, so `Modifier.zIndex` is accounted for. An Android `ViewGroup` is read in
child order, which is paint order until a view is raised by `elevation` or `translationZ`; a raised
sibling can be missed as an obstruction.

Both are rarer than they sound. Sweeping the demo app point by point — comparing what the tree
predicts against what a real touch does — the tree was right at every interactive node; the only
places the two parted company were empty ones, where Material's `Surface` takes a touch that no node
was going to get anyway. Where it matters, treat `hittable: false` as reliable and `hittable: true`
as "nothing in the tree is in the way".

Note that none of this constrains `performNodeAction`, which invokes the node's own action and never
goes near the input system. Hittability is about whether a **person** could tap it — a UI check, not
a precondition for driving the app.

### Why not just tap it and see?

Because a tap is not a question. It fires the action, changes the screen, and still does not say
what it hit — finding that out means dumping the hierarchy afterwards and undoing whatever happened.

Measured on the same emulator and screen as the [capture benchmarks](#why-not-the-cli) — the demo
app's *Compose nodes* screen, 27 nodes of which 8 are interactive:

| | answers | median |
| --- | --- | --- |
| `adb shell input tap` | nothing — it only fires | 329 ms |
| `adb shell input tap` + `uiautomator dump` | one point, having changed the screen | 3,621 ms |
| **`nodeAt`** | one point, from the tree | **30 ms** |
| **`getNodeTree`** | **every** node's reachability at once | **30 ms** |

One capture carries the whole screen's answer, so per interactive node it is about 4 ms — against
329 ms per point for a tap that also has to be undone.

The ratio is the smaller half of it. What the numbers buy is *frequency*: at 30 ms an agent can ask
after every action, which at three seconds it cannot. And the answer is a node with its `testTag`,
role and id — something to act on next — rather than a rectangle.

Treat all of these as indicative. They come from an emulator, which is slower than a device, and a
bigger screen means more nodes.

## MCP tools

The plugin contributes six tools to the host's [MCP server](/guide/mcp-server). As with every
plugin tool, JetWhale injects the `sessionId` parameter and routes the call to the right session.

### `com.kitakkun.jetwhale.semantics.findNodes`

The one to reach for first. Captures the tree and returns the matching nodes as a flat list, each
carrying the `rootId`/`id` pair that addresses it, screen-pixel `bounds`, and a ready-made `tap`
point.

Criteria (`text`, `contentDescription`, `testTag`, `resourceId`, `role`) are combined with AND and
match case-insensitively by substring unless `exact` is set — `resourceId` is the exception, always
compared whole, because a resource id is an identifier rather than a label. With no criteria at all
it lists everything interactive on screen — a good way to answer "what can I do here?".

An Android `View` node is marked with `"kind": "View"` and carries its `viewClass` and `resourceId` —
see [Android View support](#android-view-support).

### `com.kitakkun.jetwhale.semantics.getNodeTree`

The whole tree, structure included. Takes `merged`, `includeInvisible`, `maxDepth`,
`interactiveOnly` and `rootId`. Use it when the layout itself is the question; use `findNodes` when
you are looking for one element.

### `com.kitakkun.jetwhale.semantics.nodeAt`

Which node a tap at a screen coordinate would be dispatched to, or `null` when nothing there takes
touch input. The question you have once you have picked a point from a screenshot rather than from a
node's own bounds — see [Can a finger reach it?](#can-a-finger-reach-it).

### `com.kitakkun.jetwhale.semantics.performNodeAction`

Invokes a node's own semantics action: `Click`, `LongClick`, `SetText`, `InsertText`, `ImeAction`,
`ScrollBy`, `RequestFocus`, `Dismiss`, `Expand`, `Collapse`. On an Android `View` node it runs the
view's own equivalent — see [Android View support](#android-view-support).

This runs the action the node itself declared, so it needs no coordinates and cannot land on
whatever moved into that spot in the meantime — prefer it over `adb shell input tap`. `rootId` is
optional: without it the node is looked up in the most recent capture.

It is also the more *reliable* route, not just the tidier one: on the emulator used for the
[benchmarks](#why-not-the-cli), `adb shell input swipe` did not scroll a `LazyColumn` at all, while
`ScrollBy` moved it by exactly the requested distance on the first try.

A typical agent loop:

```
findNodes(testTag: "login-button")     → { "nodes": [{ "rootId": "compose-root-1f2e", "id": 42, … }] }
performNodeAction(nodeId: 42, action: "Click")
findNodes()                            → the new screen's interactive nodes
```

### `com.kitakkun.jetwhale.semantics.getViewAttributes`

Reads one Android `View` node's platform attributes, addressed by `rootId` and `nodeId`. Each entry
carries its `id` (what `setViewAttribute` names), `label`, `group`, `type`, `value` as a string, the
`options` of an enum, and `editable: false` when it cannot be written. A node that has no attributes
— a Compose node — comes back as a `message` rather than an error. See
[Editing View attributes](#editing-view-attributes).

`type` is one of `bool`, `int`, `float`, `text`, `color`, `dimension`, `enum` and `layoutSize`, and
it is the whole of what the attribute takes: a `dimension` is a pixel figure and nothing else, an
`enum` is one of its `options` and nothing else. A `layoutSize` — `layout.width`, `layout.height` —
is the one that takes either, and it lists its `constants` whichever it currently reads as, so both
possibilities are visible from a single read:

```
{ "id": "layout.width", "type": "layoutSize", "value": "WRAP_CONTENT",
  "constants": ["MATCH_PARENT", "WRAP_CONTENT"] }
{ "id": "layout.width", "type": "layoutSize", "value": "500.0", "dp": 250.0,
  "constants": ["MATCH_PARENT", "WRAP_CONTENT"] }
```

### `com.kitakkun.jetwhale.semantics.setViewAttribute`

Changes one attribute: `rootId`, `nodeId`, `attributeId`, and `value` as a **string**, read according
to the attribute's own type — `"GONE"`, `"true"`, `"0.5"`, `"#80FF0000"`, `"24"` — so there is no
sealed JSON to construct. A `layoutSize` takes one of its constants, case-insensitively, or a pixel
figure: `"wrap_content"`, `"match_parent"`, `"500"`. The answer carries the attribute as it reads back afterwards, which is not
always what was asked for: an app may clamp a value or ignore it. The edit is temporary, and only
`View` nodes have attributes at all.

```
findNodes(resourceId: "status")   → { "nodes": [{ "rootId": "android-window-1f2e", "id": -4, "kind": "View" }] }
getViewAttributes(rootId: "android-window-1f2e", nodeId: -4)
setViewAttribute(rootId: "android-window-1f2e", nodeId: -4, attributeId: "textColor", value: "#FF0000FF")
```

## Why not the CLI?

`android layout` and `adb shell uiautomator dump` both go out to the accessibility framework across
a process boundary and write a file on the device before anything can read it. This plugin reads the
semantics tree **inside** the app, on its main thread, and sends it back over the JetWhale
connection that is already open — so a capture costs about as much as a frame.

Measured on one machine, on the same screen, back to back — a Pixel-class emulator (1080×2400,
density 2.625) showing the demo app's *Compose nodes* screen, 38–39 elements:

| | median | 
|---|---|
| **`com.kitakkun.jetwhale.semantics.getNodeTree`** (host → app → host) | **14 ms** (min 12, p90 15) |
| ⤷ of which reading the tree on the device | **1 ms** (max 6) |
| **`com.kitakkun.jetwhale.semantics.findNodes`** | **11 ms** |
| `adb shell uiautomator dump` + `adb pull` | 1,960 ms |
| `android layout` (Google's Android CLI) | 2,703 ms |

That is **~190× faster than `android layout`** on this setup — fast enough that an agent can capture
between every action instead of budgeting for the dump. Treat the ratio as indicative rather than a
spec: an emulator is slower than a physical device, and a bigger screen means more nodes.

The host shows both numbers live — what the capture cost on the device, and the round trip from the
host — so a slow capture says where the time went.

The tree is also richer than what the CLIs return: `android layout` gives a flat list with text,
`content-desc` and bounds, but no `testTag`, no role and no per-node id, so an agent can only aim by
label or by pixel. This plugin reports all three, which is what makes
[`performNodeAction`](#com-kitakkun-jetwhale-semantics-performnodeaction) able to address a node
directly.

### Are the coordinates right?

`bounds` and `tap` are screen pixels, so they have to survive whatever the device does to the
window. They were checked two ways at once — cross-checked against `android layout`'s reading of the
same screen, and proved by tapping the reported point and watching the intended node react — across
the conditions that move a window around:

| Condition | nodes cross-checked | worst disagreement | tap reached the node |
|---|---|---|---|
| gesture nav, portrait, density 420 | 19 | 1 px | ✅ |
| 3-button navigation bar | 18 | 1 px | ✅ |
| landscape | 10 | 1 px | ✅ |
| density 320 | 29 | 1 px | ✅ |
| 800×1280 @ density 320 | 12 | 1 px | ✅ |

The residual 1 px is rounding: this plugin rounds, `android layout` truncates.

Each condition was also re-run with a dialog open, which is the case that actually exercises the
arithmetic — a dialog is its own window and does **not** start at the screen origin, so a
window-relative coordinate reported as if it were absolute would be off by the whole offset. In
landscape that offset reaches `(717, 298)`; the dialog's nodes still agreed to within 1 px, and
tapping the reported point closed the dialog. Every root reports its own `windowOffset`, so a
suspicious coordinate can be traced back to the window it came from.

## Platform support

The capture and action layer is written against `SemanticsOwner`, which lives in Compose's
**common** source set — so it is the same code on every target. What differs is only how a probe
*finds* an owner, and that is where platform support begins and ends.

| Target | Probe | What you write |
|---|---|---|
| **Android** | ✅ | `installJetWhaleSemanticsProbe(application)`, or `JetWhaleSemanticsProbe()` in a composition — captures the window, Android `View`s included |
| **Desktop (JVM)** | ✅ | `JetWhaleSemanticsProbe()` inside your `Window { }`, under `@OptIn(ExperimentalComposeUiApi::class)` |
| iOS, JS, Wasm | — | the standard entry points expose no owner — see [iOS and web](#ios-and-web) |

Those are the targets the agent artifact ships for — Compose Multiplatform's own set. Linux, mingw
and macOS are absent: the first two have no `androidx.compose.ui` at all, and macOS needs the whole
build to opt into Compose's experimental support for it.

Android finds roots process-wide through the callback Compose fires when it creates the view backing
a composition. Desktop has no such callback, so its probe is scoped to a window and reads
`ComposeWindow.semanticsOwners` — which is snapshot-backed, so a dialog or popup rendered inside
that window appears and disappears on its own. A second `Window { }` is a second composition and
needs its own call. `ComposeWindow.semanticsOwners` is `@ExperimentalComposeUiApi`, so the desktop
probe carries that marker too — opt in at the call site
(`@OptIn(ExperimentalComposeUiApi::class)`), or the module will not compile. The Android probe has
no such requirement.

### iOS and web

There is no probe on iOS, JS or Wasm because Compose Multiplatform hands out no `SemanticsOwner` for
the scene your app actually shows — not to a probe, and not to your own code either.

`ComposeUIViewController`, `ComposeUIView` and `ComposeViewport` build their `ComposeScene`
internally. `PlatformContext.SemanticsOwnerListener` is the seam that would deliver the owners, but
it is only consulted for a scene the caller constructs, so an app cannot supply one. Nor is there a
route from inside the composition: `SemanticsNode.root` is public, but reaching a `SemanticsNode`
needs an owner to begin with.

Desktop was in the same position until Compose Multiplatform 1.10 exposed
`ComposeWindow.semanticsOwners`; iOS and web have no equivalent yet. The plugin's capture and action
layer is already common code, so a probe for both targets is a small addition once an owner can be
reached.

Until then, `registerSemanticsOwner` is the seam to use for any host you build yourself on top of
`ComposeScene` — including `ImageComposeScene`, whose `semanticsOwners` *is* available on these
targets:

```kotlin
import com.kitakkun.jetwhale.plugins.semantics.agent.ComposeNodeSourceRegistry
import com.kitakkun.jetwhale.plugins.semantics.agent.registerSemanticsOwner

ComposeNodeSourceRegistry.registerSemanticsOwner(
    owner = mySemanticsOwner,
    sourceId = "main-window",
    label = "Main window",
    density = 2f,
)
```

::: tip Which thread reads the tree
Semantics may only be read on the thread that owns the composition, and the probes get there without
adding a dependency to your app — a `Handler` on Android, `EventQueue` on desktop. A target without
a probe falls back to `Dispatchers.Main`, which on desktop would need `kotlinx-coroutines-swing` on
your classpath; pass your own `ComposeUiThread` to `registerSemanticsOwner` if that does not suit.
:::

## Troubleshooting

**"The app reported no Compose root."** No probe is installed. Add
`installJetWhaleSemanticsProbe(application)`, or `JetWhaleSemanticsProbe()` inside your
composition. On iOS, JS and Wasm this is expected — see [iOS and web](#ios-and-web).

**A dialog's contents are missing.** A dialog is a separate window. The Application-level probe
finds it; an in-composition probe only registers the window it was called in. A dialog built from
Android views with no Compose in it is not captured at all — see
[Android View support](#android-view-support).

**An action comes back `performed: false`.** The message says why — the node does not expose that
action, it is disabled, or its handler declined. Capture the tree again and check the node's
`actions` list; only what is listed there can be invoked.

**Node ids changed between calls.** A node's id is stable while it stays composed — a `View` node's,
while the view is alive — and ids are only unique within their root. After anything that recomposes
the screen, capture again rather than reusing ids.
