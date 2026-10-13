# Semantics Tree

What the [Compose Semantics Inspector](/guide/compose-semantics-inspector) captures, and how its MCP
tools read the capture and act on it. For setup and the host UI, see the plugin's guide.

## Roots and node types

The tree is the **semantics** tree: the tree an accessibility service sees, and the one that says
what is actually clickable. A `Box` that only lays out pixels does not appear on its own; a `Button`
does, carrying its label and its `OnClick` action.

| Platform | Read from | A root is | Unit |
|---|---|---|---|
| Android | Compose, plus the Android `View`s around and inside the composition | One window; a dialog or popup is a root of its own | pixels (`"px"`) |
| Desktop (JVM) | Compose, through the window's `SemanticsOwner`s | One composition; a dialog or popup is a root of its own | pixels |
| iOS | The accessibility protocol, which UIKit, SwiftUI and Compose all publish into | One `UIWindow`; a sheet or alert stays inside the window that showed it | points (`"pt"`) |

Every node in the MCP JSON names its `unit`, and every root reports its own `windowOffset`. A node's
`type` is `"compose"`, `"view"` or `"apple"`; all three fill the same fields — `text`,
`contentDescription`, `bounds`, `actions`, and the `enabled` / `clickable` / `editable` /
`scrollable` flags — so a consumer that only reads the tree needs no special cases. Host and agent
have to be built against the same protocol version: a node type the other side does not know fails
to decode rather than degrading.

Two views are available, switchable in the host and per MCP call:

| | What you get |
|---|---|
| **Merged** (default) | A `Button`'s label is folded into the clickable node: one node per control. This is what accessibility services and `performClick` see, and usually what you want. |
| **Unmerged** | Every semantics node stays separate, closer to how the UI is written. Useful to see exactly which composable contributed which property. |

## Android View nodes

A screen usually sits in an Activity's or a Fragment's layout, and an `AndroidView { }` puts a `View`
back inside the composition. On Android the capture follows both crossings, so a root is **one
window**, not one composition:

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

| | `View` node |
|---|---|
| `id` | **negative**, assigned by the agent and valid while the view is alive. Compose's semantics ids are non-negative, so the two never collide |
| `viewClass` | the view's class, e.g. `android.widget.Button` |
| `resourceId` | the entry name of its `android:id`, e.g. `submit` for `@id/submit`. A `View` has no `testTag`; this plays that role |

A Compose node carries `role`, `testTag` and `stateDescription`, which a `View` has no counterpart
for.

`performNodeAction` on a `View` node runs the view's own API rather than a synthesized tap: `Click` →
`performClick()`, `LongClick` → `performLongClick()`, `SetText` / `InsertText` on an `EditText`,
`ImeAction` → `onEditorAction`, `ScrollBy` → `scrollBy`, `ScrollToIndex` →
`RecyclerView.scrollToPosition` / `ListView.setSelection`, `BringIntoView` →
`requestRectangleOnScreen`, `RequestFocus` → `requestFocus()`. `Dismiss`, `Expand` and `Collapse`
have no `View` counterpart and come back `performed: false` saying so.

A window with no Compose in it is not captured: the composition is what announces a window, so a
plain `AlertDialog` built from views does not appear. A merged capture can also fold an
`AndroidView` away, since the embedded views hang off the node the `AndroidView { }` creates; when an
ancestor merges its descendants, that node and the views under it go with it. Capture unmerged
(`merged: false`) to see them.

### View attributes

A `View` node exposes its **platform attributes**, the properties a Layout Inspector shows, and most
can be changed live. They are fetched per node when asked for, never as part of a capture.

| Group | Attributes |
|---|---|
| **State** | `visibility` (`VISIBLE`/`INVISIBLE`/`GONE`), `enabled`, `selected`, `activated`, `clickable`, `focusable`, and `focused` read-only |
| **Layout** | `layout.width` / `layout.height` (`MATCH_PARENT`, `WRAP_CONTENT` or a pixel length), `padding.*`, `margin.*` (when the parent hands out margins), `minWidth` / `minHeight`, and `bounds` read-only |
| **Appearance** | `alpha`, `backgroundColor` (when the background is a flat color; otherwise `background` names the drawable, read-only), `elevation`, `translationX` / `translationY`, `rotation`, `scaleX` / `scaleY` |
| **Text** | on a `TextView`: `text`, `hint`, `textSize`, `textColor`, `maxLines` |
| **Info** | `id` (`@id/name`) and `class`, both read-only |

Each attribute has one `type`, which decides its editor and what it accepts: `bool`, `int`, `float`,
`text`, `color`, `dimension` (a pixel figure), `enum` (one of its `options`) and `layoutSize`.
`layoutSize` — `layout.width` and `layout.height` — takes either one of its `constants` or a pixel
figure, and lists the constants whichever it currently reads as.

The list is curated, not reflection: every attribute is named explicitly, so the list says exactly
what the agent will touch. Compose nodes have no attributes, because writing to a projection of
composition state would be undone by the next recomposition.

## iOS nodes

On iOS the capture reads the **accessibility protocol**, because Compose Multiplatform hands out no
`SemanticsOwner` for the scene an app shows. Every toolkit publishes into that protocol, so one walk
per window covers UIKit, SwiftUI and Compose, whichever contains which:

```
UIWindow                                  ← the root; one per app, dialogs and sheets included
└─ _UIHostingView                         ← SwiftUI
   ├─ AccessibilityNode · swiftui-button  ← a SwiftUI Button
   ├─ UITextField · swiftui-name          ← a SwiftUI TextField is a real UITextField
   └─ ComposeContainerView                ← where Compose starts
      └─ AccessibilityRoot
         └─ AccessibilityElement · increment-button   ← a Compose Button, testTag and all
```

Every node on iOS is an `"apple"` node; Compose content arrives through the same protocol as the
rest.

| | `apple` node |
|---|---|
| `id` | **negative**, assigned by the agent and valid while the object is alive |
| `className` | the Objective-C class: `UITextField`, `SwiftUI.AccessibilityNode`, Compose's `AccessibilityElement` |
| `accessibilityIdentifier` | where SwiftUI's `.accessibilityIdentifier(_:)` **and** a Compose `Modifier.testTag` both land; `findNodes(testTag:)` matches it |
| `accessibilityValue` | the value as the toolkit reports it: a switch's `"1"`, a slider's `"50%"` |
| `traits` | the set `UIAccessibilityTraits`, by name: `Button`, `Selected`, `NotEnabled`, `ToggleButton`… |

Coordinates are **points**, the unit every iOS tool takes — `idb ui tap X Y` takes them as they are —
and the root's `density` is `1`. `merged` has no effect: the accessibility tree is the merged one.

A view marked `accessibilityViewIsModal`, such as a presented sheet or an alert, hides its siblings
as it does from VoiceOver, so a default capture leaves out what is behind it and `includeInvisible`
brings it back, marked as such.

What the protocol does not carry, the capture cannot report: a Compose `role` and `stateDescription`
are folded into label and traits, and a node scrolled out of its container reports its laid-out
frame, clipped to the window rather than to the container. A Compose scrollable does read
`scrollable`, through the `UIFocusItemScrollableContainer` its element conforms to, which also gives
`ScrollBy` a real distance there.

`performNodeAction` runs the protocol's counterpart, or the view's own API when the node is a view
that has one:

| Action | UIKit view | SwiftUI node | Compose element |
|---|---|---|---|
| `Click` | `accessibilityActivate()`, else the control's touch-up actions | `accessibilityActivate()` — runs the `Button`'s closure, flips a `Toggle` | `accessibilityActivate()` — runs `onClick`, toggles a `Checkbox` |
| `SetText` / `InsertText` | on a `UITextField` / `UITextView`; `InsertText` focuses the field first, as a keystroke needs | same: the `TextField` is a `UITextField` underneath | not available — the element's value is read-only |
| `ImeAction` | the field's delegate `textFieldShouldReturn:` | same — where `onSubmit` lives | not available |
| `ScrollBy` | `UIScrollView.setContentOffset`, by the distance asked | a `ScrollView` is a `UIScrollView` underneath; a bare node gets `accessibilityScroll`, by **direction**: one page | by the distance asked, through the `UIFocusItemScrollableContainer` the element conforms to |
| `ScrollToIndex` | `UITableView` / `UICollectionView`, the index counted across sections | a `List` is a `UICollectionView` | not available |
| `BringIntoView` | every `UIScrollView` above the view | a bare node only reports whether it is already in view | same |
| `RequestFocus` | `becomeFirstResponder()` | on the backing `UITextField` | not available |
| `Dismiss` | `accessibilityPerformEscape()`, tried on any node | same | same |
| `Expand` / `Collapse` | a custom action of that name, when it has a handler block; a target/selector custom action is not invoked | same | same |
| `LongClick` | not available | | |

A secure field's contents are never captured: a password field stays `isEditable` with no
`editableText`, on Android as on iOS.

## Reachability

Before it acts, an agent wants to know one thing: `operable`. A node is operable when it offers
something to do (an action, editable content or scrolling), is enabled, and a gesture it accepts
actually reaches it. A node that offers something but fails one of those is marked
`"operable": false`, with `enabled`, `hittable` and `obscuredBy` saying which; the host tags the row
**not operable**, and `findNodes(operableOnly: true)` keeps only the nodes that pass. A label is
never marked either way.

The reachability half is its own flag. A node that accepts touch input but cannot receive one is
marked `"hittable": false`, with `obscuredBy` naming what takes the touch instead. It is worked out
from the capture alone, with no tap sent, by walking the windows from the top down and, within a
window, the last-drawn node first, the way the platform dispatches a touch:

| The button is… | Reported as |
| --- | --- |
| under a dialog or a popup | `hittable: false`, `obscuredBy` the node on top |
| behind a modal window, anywhere on screen | `hittable: false`, `obscuredBy` that window's root |
| scrolled out of its container | `hittable: false`, no `obscuredBy`: no area to aim at |
| under a later sibling that takes touches | `hittable: false`, `obscuredBy` that sibling |
| a clickable row whose center is its own button | `hittable: false`, `obscuredBy` that button: the tap is consumed there |
| a list whose center is one of its own rows | `hittable: true`: a drag starting on the row still scrolls the list |

The last two differ because the gesture does: a tap stops at the deepest node that takes it, while a
drag is seen by every scrollable on the way down. So `obscuredBy` on a scrollable only names
something outside it, and a node that is both scrollable and clickable reads `hittable: true` when
either gesture gets through.

Two things a capture cannot see, and both make it optimistic: an overlay that consumes touches
without exposing semantics (a bare `pointerInput`, an `OnTouchListener`), and a gesture an ancestor
swallows before the node sees it. On the Compose side the drawing order is guaranteed, so
`Modifier.zIndex` is accounted for; an Android `ViewGroup` is read in child order, so a sibling raised
by `elevation` or `translationZ` can be missed. Treat `hittable: false` as reliable and
`hittable: true` as "nothing in the tree is in the way".

None of this constrains `performNodeAction`, which invokes the node's own action and never goes near
the input system. Hittability is about whether a **person** could tap the node.

## MCP tools

As with every plugin tool, JetWhale injects the `sessionId` parameter and routes the call to the
right session.

### `com.kitakkun.jetwhale.semantics.findNodes`

The one to reach for first. Captures the tree and returns the matching nodes as a flat list, each
carrying the `rootId` / `id` pair that addresses it, its `bounds` on screen, and a ready-made `tap`
point, both in the node's `unit`.

Criteria (`text`, `contentDescription`, `testTag`, `resourceId`, `role`) are combined with AND and
match case-insensitively by substring unless `exact` is set; `resourceId` is always compared whole,
because it is an identifier rather than a label. With no criteria it lists everything interactive on
screen, a good way to answer "what can I do here?". Add `operableOnly: true` to narrow that to what
the user could operate right now. An Android `View` node is marked `"kind": "View"` and carries its
`viewClass` and `resourceId`.

### `com.kitakkun.jetwhale.semantics.getNodeTree`

The whole tree, structure included. Takes `merged`, `includeInvisible`, `maxDepth`,
`interactiveOnly`, `rootId` and `format` (see [Compact text output](#compact-text-output)). Use it
when the layout itself is the question, and `findNodes` when you are looking for one element.

### Compact text output

`getNodeTree` and `findNodes` return JSON unless the call passes `format: "text"`. The same nodes then
come back as an outline, one line per node, indented by depth, for an agent that reads the tree
rather than parses it:

```text
root compose-root-1f2e "MainActivity" unit=px density=2.0
- node #1 tap=540,1200
  - node #2 tap=540,100
    - Button #3 desc="Navigate up" [clickable] actions=Click tap=80,120
    - node #4 "Settings" tap=390,120
  - node #5 tag=settings-list [scrollable] actions=ScrollBy,ScrollToIndex tap=540,1200
    - node #100 [clickable] actions=Click tap=540,370
      - node #200 "Setting item number 0" tap=374,345
      - node #300 "Description for item 0" tap=374,390
      - Switch #400 [clickable] actions=Click tap=970,370
    …
```

Each line starts with the node's role (a Compose node), its class (an Android View or an iOS node),
or `node` when it has neither, then `#id`; with the root line's `rootId`, that is the pair
`performNodeAction` takes. What follows is only what is present: the text in quotes, `desc=`,
`input=` (a text field's content), `toggle=`, `tag=` / `resId=` / `axId=`, the surprising side of each
flag in brackets (`[clickable]`, `[disabled]`, `[unhittable]`, …), `actions=` (the names
`performNodeAction` accepts), and the `tap` point in the root's `unit`. Text is quoted as a JSON
string, so a quote or a line break in it cannot break the line. Bounds are left out; ask for JSON
when a layout question needs them. A `findNodes` line adds `root=<rootId>` after the id, since a flat
list has no root lines.

On a 67-node settings screen the outline is 3.8 KB where the JSON is 10 KB.

### `com.kitakkun.jetwhale.semantics.nodeAt`

Which node a tap at a screen coordinate would be dispatched to, or `null` when nothing there takes
touch input: the question you have once you picked a point from a screenshot rather than from a
node's bounds. See [Reachability](#reachability).

### `com.kitakkun.jetwhale.semantics.performNodeAction`

Invokes a node's own semantics action: `Click`, `LongClick`, `SetText`, `InsertText`, `ImeAction`,
`ScrollBy`, `ScrollToIndex`, `RequestFocus`, `Dismiss`, `Expand`, `Collapse`, plus `BringIntoView`,
which works on any node. Only what a node lists in `actions` can be invoked, except `BringIntoView`.
On an Android `View` node it runs the [view's own equivalent](#android-view-nodes); on iOS, the
[accessibility protocol's](#ios-nodes).

It needs no coordinates and cannot land on whatever moved into that spot meanwhile, so prefer it
over `adb shell input tap`. It is also the more reliable route: on the emulator used for the
[measurements](#measurements), `adb shell input swipe` did not scroll a `LazyColumn` at all, while
`ScrollBy` moved it by exactly the requested distance. `rootId` is optional; without it the node is
looked up in the most recent capture.

```
findNodes(testTag: "login-button")     → { "nodes": [{ "rootId": "compose-root-1f2e", "id": 42, … }] }
performNodeAction(nodeId: 42, action: "Click")
findNodes()                            → the new screen's interactive nodes
```

**Getting a node on screen.** `BringIntoView` scrolls a node in the way accessibility's "show on
screen" does: every scrollable ancestor moves by the least amount that reveals the whole node,
innermost first, and on Android the window's `View`s around the composition move too, so it crosses
a `LazyColumn` inside a `ScrollView`, or a `RecyclerView` inside an `AndroidView { }`, in one call. A
node already fully visible reports `performed: true` with a note that nothing moved.

```
findNodes(text: "Terms of service")    → { "nodes": [{ "id": 87, "isVisible": false, … }] }
performNodeAction(nodeId: 87, action: "BringIntoView")
getNodeTree()                          → node 87 now has on-screen bounds
```

A lazy container only composes the items near its viewport, so an item far down a `LazyColumn` has
no node yet. `ScrollToIndex` is the step before: invoke it on the *container* with the item's
`index`, then capture again. `LazyColumn`, `LazyRow`, the lazy grids and `Pager` expose it, and on
the View side `RecyclerView` and `ListView`.

```
findNodes(testTag: "feed")             → { "nodes": [{ "id": 12, "actions": ["ScrollBy", "ScrollToIndex", …] }] }
performNodeAction(nodeId: 12, action: "ScrollToIndex", index: 240)
findNodes(text: "Item 240")            → now composed, and BringIntoView can finish the job if needed
```

Both scrolls are applied by the container on its next frame, so a capture taken in the same breath
still shows the old bounds; capture again after.

### `com.kitakkun.jetwhale.semantics.getViewAttributes`

Reads one Android `View` node's [attributes](#view-attributes), addressed by `rootId` and `nodeId`.
Each entry carries its `id` (what `setViewAttribute` names), `label`, `group`, `type`, `value` as a
string, the `options` of an enum, and `editable: false` when it cannot be written. A node with no
attributes, such as a Compose node, comes back as a `message` rather than an error.

```
{ "id": "layout.width", "type": "layoutSize", "value": "WRAP_CONTENT",
  "constants": ["MATCH_PARENT", "WRAP_CONTENT"] }
{ "id": "layout.width", "type": "layoutSize", "value": "500.0", "dp": 250.0,
  "constants": ["MATCH_PARENT", "WRAP_CONTENT"] }
```

### `com.kitakkun.jetwhale.semantics.setViewAttribute`

Changes one attribute: `rootId`, `nodeId`, `attributeId`, and `value` as a **string**, read according
to the attribute's type — `"GONE"`, `"true"`, `"0.5"`, `"#80FF0000"`, `"24"` — so there is no sealed
JSON to construct. A `layoutSize` takes one of its constants, case-insensitively, or a pixel figure:
`"wrap_content"`, `"match_parent"`, `"500"`. The answer carries the attribute as it reads back
afterwards, which is not always what was asked for: an app may clamp a value or ignore it. The edit
is temporary.

```
findNodes(resourceId: "status")   → { "nodes": [{ "rootId": "android-window-1f2e", "id": -4, "kind": "View" }] }
getViewAttributes(rootId: "android-window-1f2e", nodeId: -4)
setViewAttribute(rootId: "android-window-1f2e", nodeId: -4, attributeId: "textColor", value: "#FF0000FF")
```

## Custom scenes and threading

There is no probe on JS or Wasm: `ComposeViewport` builds its scene internally, as iOS does, and the
browser has no accessibility tree the agent could read instead. Desktop was in the same position
until Compose Multiplatform 1.10 exposed `ComposeWindow.semanticsOwners`. The capture and action
layer is common code, so a probe is a small addition once an owner can be reached.

Until then, `registerSemanticsOwner` is the seam for any host you build yourself on top of
`ComposeScene`, including `ImageComposeScene`, whose `semanticsOwners` *is* available there:

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

Semantics may only be read on the thread that owns the composition. The probes get there without
adding a dependency to your app: a `Handler` on Android, `EventQueue` on desktop. A source without a
probe falls back to `Dispatchers.Main`, which on desktop needs `kotlinx-coroutines-swing` on your
classpath; pass your own `ComposeUiThread` to `registerSemanticsOwner` if that does not suit.

## Measurements

The plugin reads the tree **inside** the app, on its main thread, and sends it over the JetWhale
connection that is already open, so a capture costs about as much as a frame. The figures below come
from an emulator, which is slower than a device, and a bigger screen means more nodes; treat them as
indicative.

::: details Capture speed against the CLIs
`android layout` and `adb shell uiautomator dump` both go out to the accessibility framework across a
process boundary and write a file on the device before anything can read it. Measured on one machine,
on the same screen, back to back — a Pixel-class emulator (1080×2400, density 2.625) showing the demo
app's *Compose nodes* screen, 38–39 elements:

| | median |
|---|---|
| **`com.kitakkun.jetwhale.semantics.getNodeTree`** (host → app → host) | **14 ms** (min 12, p90 15) |
| ⤷ of which reading the tree on the device | **1 ms** (max 6) |
| **`com.kitakkun.jetwhale.semantics.findNodes`** | **11 ms** |
| `adb shell uiautomator dump` + `adb pull` | 1,960 ms |
| `android layout` (Google's Android CLI) | 2,703 ms |

That is about 190× faster than `android layout` on this setup, fast enough to capture between every
action. The host shows both numbers live — the capture's cost on the device and the round trip — so
a slow capture says where the time went.

The tree is also richer: `android layout` gives a flat list with text, `content-desc` and bounds, but
no `testTag`, no role and no per-node id, so an agent can only aim by label or by pixel.
:::

::: details Coordinate accuracy
`bounds` and `tap` are screen coordinates, so they have to survive whatever the device does to the
window. They were cross-checked against `android layout`'s reading of the same screen, and proved by
tapping the reported point and watching the intended node react:

| Condition | nodes cross-checked | worst disagreement | tap reached the node |
|---|---|---|---|
| gesture nav, portrait, density 420 | 19 | 1 px | ✅ |
| 3-button navigation bar | 18 | 1 px | ✅ |
| landscape | 10 | 1 px | ✅ |
| density 320 | 29 | 1 px | ✅ |
| 800×1280 @ density 320 | 12 | 1 px | ✅ |

The residual 1 px is rounding: this plugin rounds, `android layout` truncates. Each condition was
also rerun with a dialog open, the case that exercises the arithmetic: a dialog is its own window and
does not start at the screen origin. In landscape that offset reaches `(717, 298)`; the dialog's nodes
still agreed to within 1 px, and tapping the reported point closed the dialog.
:::
