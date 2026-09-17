# Compose Semantics Inspector on iOS: UIKit, SwiftUI and Compose through one tree

Status: **implemented** on this branch, in `jetwhale-plugins/semantics/agent/src/iosMain`, and
verified end to end against the iOS demo through the host's MCP tools. The findings below were
established first, and the design follows from them.

## Goal

Give the Compose Semantics Inspector an iOS agent that captures what is on screen and drives it,
the way the Android agent does for `View`s and compositions: UIKit views, SwiftUI content, and the
Compose Multiplatform content the app hosts, in one tree per window, addressable by the existing
`CaptureNodeTree` / `PerformNodeAction` protocol.

Before this, iOS had no support at all: Compose Multiplatform hands out no `SemanticsOwner` for
`ComposeUIViewController`, so the common `SemanticsOwnerNodeSource` had nothing to read.

## What was established first

All of it from Kotlin/Native in `iosMain`, against `platform.UIKit` cinterop, with no Swift in the
path and VoiceOver off. iOS 26.2 simulator, Compose Multiplatform 1.11.1.

| Question | Answer |
|---|---|
| Can Kotlin walk the UIKit tree? | Yes. `UIApplication.sharedApplication.connectedScenes` → `UIWindowScene.windows` → `UIView.subviews`. |
| Do SwiftUI views appear? | Yes, as `SwiftUI.AccessibilityNode` objects in the hosting view's `accessibilityElements`, with `accessibilityLabel`, `accessibilityValue`, `accessibilityIdentifier` (from `.accessibilityIdentifier`), `accessibilityTraits`, `accessibilityFrame`. |
| Can Kotlin act on SwiftUI? | `accessibilityActivate()` on a `Button` incremented its `@State` counter; on a `Toggle` it flipped the binding. |
| SwiftUI `TextField`? | It is a real `UITextField` (`PlatformTextFieldAdaptor`). Setting `text` and sending `UIControlEventEditingChanged` updated the SwiftUI binding. |
| Are SwiftUI node objects stable? | Yes: the same `AccessibilityNode` instances came back across captures seconds apart. |
| Does Compose Multiplatform show up? | Yes, always, VoiceOver or not: `ComposeContainerView` → `OverlayInputView` carries an `AccessibilityRoot` whose `AccessibilityElement` children mirror the semantics tree. `Modifier.testTag` arrives as `accessibilityIdentifier`, `Text` as `accessibilityLabel`, `Disabled` as the `NotEnabled` trait, selection as the `Selected` trait. |
| Can Kotlin act on Compose? | `accessibilityActivate()` on `increment-button` changed its label to `Clicked 1 time(s)`; on `demo-checkbox` it set the `Selected` trait; `accessibilityScroll(down)` paged the `LazyColumn`; `accessibilityPerformEscape()` closed a Compose `Dialog`. |
| Are Compose element objects stable? | Same instances across captures while the item stays composed; a `LazyColumn` item that scrolls out and back is a new element. |
| Where do dialogs live? | In the **same** `UIWindow`. A SwiftUI `.alert` is a `_UIAlertControllerPhoneTVMacView` under a second `UITransitionView`; a Compose `Dialog` is a second, full-window `ComposeContainerView` with its own `AccessibilityRoot`, and the content underneath is hidden from accessibility while it is up. Neither opens a new window. |
| `UIWindow.hitTest`? | Answers with the hosting view for a SwiftUI button, `OverlayInputView` for Compose, the `UITextField` for the field, and the alert's dimming `UIView` once an alert is up. Not used by the implementation: the common `NodeHitTesting` resolves reachability from the captured tree, as on Android. |

What did **not** work:

- Writing to a Compose text field through accessibility: `setValue(_:forKey: "accessibilityValue")`
  on the `AccessibilityElement` returned without effect. Text entry into Compose on iOS stays open
  (see below).
- `accessibilityElementCount()` returns `NSNotFound` on a non-container, which reads as a huge
  positive count. The walker only uses `accessibilityElements`.
- A Kotlin `WeakReference` to an Objective-C object. The reference is to the Kotlin wrapper, which
  the runtime collects while the object lives on, so an id registry built on it forgot every node
  before the first action arrived. Objects are held strongly instead, scoped to the latest capture.
- The Kotlin cast to `UIAccessibilityIdentificationProtocol` answers `null` even for a
  `UITextField`. The identifier is read by selector.

## Architecture

### One walker, three toolkits

On Android the agent has two readers stitched together: a `View` walk, and a `SemanticsOwner` read
wherever the walk meets a composition. On iOS there is no owner to read, but the accessibility tree
that Compose Multiplatform publishes is the same semantics tree projected onto `NSObject`
accessibility, and SwiftUI publishes its tree the same way. So iOS gets **one** walker over the
`NSObject` accessibility protocol (`AccessibilityTreeCapture.kt`), and it covers UIKit, SwiftUI and
Compose without knowing which is which beyond the class name.

The walk, per window:

1. Start at the `UIWindow`.
2. For an object with non-`nil` `accessibilityElements`, its children are those elements, in that
   order. This is the toolkit's own statement of its semantic tree: a SwiftUI hosting view lists its
   nodes and the interop `UIView`s it embeds; Compose's `OverlayInputView` lists its
   `AccessibilityRoot`.
3. For a `UIView` with `nil` `accessibilityElements`, its children are its `subviews` — unless the
   view is itself an accessibility element (`isAccessibilityElement`), in which case it is one
   object to accessibility and the views inside it are its implementation, so it has no children.
4. Never both. A hosting view's `accessibilityElements` repeats `UIView`s that are also reachable
   through `subviews` (the `UITextField`, the `ComposeContainerView`); taking both lists produced
   every such subtree twice.
5. A hidden view (`hidden`, `alpha == 0`) or an `accessibilityElementsHidden` container marks
   everything under it invisible; an invisible object with no surviving descendant is dropped
   unless `includeInvisible`, as on Android.

This is why it is an `iosMain` source set of the existing `agent` module, not a new module: the
walk is Kotlin against `platform.UIKit`, the way `androidMain` is Kotlin against `android.view`.

### Node model

`AppleNode` in the protocol module, `@SerialName("apple")`: `className`, `accessibilityIdentifier`,
`accessibilityValue`, `traits` (the set `UIAccessibilityTraits` bits by public name, unknown bits as
`bitN`), plus the `UiNode` members, filled as:

| `UiNode` | Source |
|---|---|
| `text` | `accessibilityLabel` |
| `editableText` | a `UITextField`/`UITextView`'s `text`; a bare text-input element's `accessibilityValue` |
| `contentDescription` | `accessibilityHint` |
| `toggleableState` | a `UISwitch`'s `on`; a `ToggleButton`-trait element's `"1"`/`"0"` value, or its `Selected` trait when it reports no value (Compose `Switch`) |
| `bounds` | `accessibilityFrame` translated into window coordinates |
| `boundsInScreen` | `accessibilityFrame` clipped to the window |
| `isEnabled` | not `NotEnabled` trait, and not a disabled `UIControl` |
| `isClickable` | `Button` / `Link` / `ToggleButton` trait, or a `UIControl` with something registered for `touchUpInside`, which is where the click fallback sends it |
| `isSelected` | `Selected` trait, or a selected `UIControl` |
| `isEditable` | `UITextField`/`UITextView`, or the text-entry trait bit both SwiftUI and Compose set on a field (no public constant; bit 18) |
| `isScrollable` | a `UIScrollView` with content beyond its bounds, or a `UIFocusItemScrollableContainer` with content beyond its visible size — Compose's element conforms to that protocol per instance, exactly when its node scrolls |
| `isFocused` | `isFirstResponder` for a `UIView`; `false` for a bare element |
| `isVisible` | not hidden, and the clipped frame is not empty |
| `isHittable` / `obscuredBy` | resolved by the common `NodeHitTesting` from the captured tree, as on every platform |
| `actions` | what the action handlers advertise for the object |

Bounds are in **points** and the root's `density` is `1`: a point is already density-independent
and is what every iOS tool takes. `merged` has no effect: the accessibility tree is the merged one.

A `ViewNode` stays Android-only rather than becoming a cross-platform "platform view" type. Its
`resourceId` and the `ViewAttributes` editing surface are shaped by `android.view.View`, and an
`apple` node has nothing to put in them.

On the host, `findNodes(testTag:)` matches an `apple` node's `accessibilityIdentifier`, which is
where a Compose `testTag` lands on iOS; the tree view tags the node `iOS` the way it tags `View`.

### Node identity

Ids are agent-assigned negatives, the range `ViewNodeIds` uses on Android. `AppleNodeIds` keys by
object address and holds every object the latest capture of a window reported **strongly**; a
capture releases the objects the window no longer shows. The strong hold is what makes the address
a sound key and keeps an id resolvable between a capture and the action that follows; the
per-capture release is what bounds the registry to one screen's worth per window. An object the
window recreates (a lazy item that scrolled out and back) gets a new id, which is the same behavior
a recomposed lazy item has on Android.

### Root discovery and lifetime

`installJetWhaleSemanticsProbe()` registers an `IosWindowNodeSource` per `UIWindow` and keeps that
set current from `UIWindowDidBecomeVisibleNotification` / `UIWindowDidBecomeHiddenNotification`.
Scene-less apps are covered through `UIApplication.windows`. Keyboard and text-effects windows are
excluded by class name. The source holds its window strongly, for the reason above, and is
unregistered when the window hides, which also releases the objects its last capture retained. Because dialogs and sheets stay in
the app's window, a normal app has one root.

The in-composition `JetWhaleSemanticsProbe()` composable is not needed on iOS: it exists on Android
for apps that cannot touch the `Application`, and on iOS the walk starts from the application anyway.

### Threading

Everything UIKit runs on the main thread. `IosUiThread` implements `ComposeUiThread` through
`dispatch_async(dispatch_get_main_queue())`, without hopping when the caller is already there.

### Actions

`IosWindowNodeSource.performAction` resolves the id to its object and dispatches on `NodeAction`
into handler objects grouped like the Android ones (`AppleNodePointerActions`,
`AppleNodeTextActions`, `AppleNodeScrollActions`, `AppleNodeStateActions`). Verified rows ✅.

| `NodeAction` | UIKit view | SwiftUI node | Compose element |
|---|---|---|---|
| `Click` | `accessibilityActivate()`, else the control's touch-up actions | `accessibilityActivate()` ✅ | `accessibilityActivate()` ✅ |
| `LongClick` | not exposed; reports not performed | same | same |
| `SetText` / `InsertText` | `UITextField`/`UITextView` | same object ✅ | not performed, with the reason |
| `ImeAction` | the delegate's `textFieldShouldReturn:` by selector | same | not performed |
| `ScrollBy` | `UIScrollView.setContentOffset`, clamped within the adjusted insets | `accessibilityScroll` by direction, one page | `UIFocusItemScrollableContainer.setContentOffset`, by distance |
| `ScrollToIndex` | `UITableView` / `UICollectionView`, the flat index translated across sections | a `List` is a `UICollectionView` | not performed |
| `BringIntoView` | every `UIScrollView` ancestor, `scrollRectToVisible`, unless each already shows the whole view | a bare element reports whether it is already in view | same |
| `RequestFocus` | `becomeFirstResponder()` | on the backing `UITextField` | not performed |
| `Dismiss` | `accessibilityPerformEscape()`, tried on any node | same | same ✅ |
| `Expand` / `Collapse` | a custom action of that name, through its handler block; a target/selector action is not invoked and is not advertised | same | same |

### Compose text entry on iOS is open

`accessibilityValue` is read-only on Compose's element. Two routes remain, neither verified:

- Compose Multiplatform installs a `UITextInput`-conforming view for the focused text field. If
  focusing the element makes that view the first responder, `insertText` on the first responder is
  ordinary `UIKeyInput` and should reach the field.
- Fall back to `performed = false` with a message that says text entry into Compose fields on iOS
  is not available, so the MCP side can report it honestly. This is what ships now.

### Pure Swift apps

This design assumes the app links a Kotlin framework that calls `installJetWhaleSemanticsProbe()`,
as the demo does from `cmpAppViewController()`. A SwiftUI app with no Kotlin of its own needs a
Swift-callable start API first; see the next section.

## Distribution to a pure Swift app

Nothing in the walker depends on Compose being present, so the same `iosMain` code serves a plain
SwiftUI or UIKit app. What such an app lacks is a way to *start* the agent and install the probe
from Swift: `startJetWhale { … }` is a receiver-lambda DSL and the plugin is a Kotlin class, neither
of which the Objective-C bridge carries usefully. The [Swift SDK design](swift-sdk-design.md)
covers the start API; this section covers packaging.

### One framework, one package

Ship a single Swift Package with one binary target, `JetWhale.xcframework`, built from a small
Kotlin **umbrella module** (`jetwhale-swift-sdk`) that depends on `jetwhale-agent-runtime`, the
official plugins' agent modules, and a `commonMain` façade with an export-clean surface. The
umbrella exports those modules into the framework (`export(...)` in the `framework { }` block), so
Swift sees one module, `JetWhale`, rather than one framework per Gradle module.

One framework rather than one per plugin, because Kotlin/Native frameworks do not compose: two
frameworks built from separate Kotlin compilations each carry their own copy of the Kotlin runtime
and of every shared class, and cannot pass objects between them. A plugin agent can only be linked
into the same framework as the runtime it registers with.

The framework is **dynamic** (`isStatic = false`): a static framework linked into an app and an app
extension duplicates symbols, and the dynamic one is what SwiftPM's `binaryTarget` distributes
cleanly. `iosArm64` and `iosSimulatorArm64` slices, `macosArm64` when the macOS agent target lands.

### The Swift-facing surface

```swift
import JetWhale

@main struct MyApp: App {
    init() {
        JetWhale.start { config in
            config.appName = "My App (staging)"
            config.endpoints.ws(host: "localhost", port: 5080)
            config.plugins.semanticsInspector()   // registers the plugin and installs the probe
            config.plugins.networkInspector()
        }
    }
}
```

`JetWhale.start` is Swift code inside the package (a `Sources/JetWhale` target next to the binary
target, so the package has a Swift target that depends on the binary one) that fills a plain
`JetWhaleSwiftConfig` value and hands it to a non-suspend Kotlin `startJetWhaleFromConfig(config)`.
Each official plugin is a method on the config rather than a type the app instantiates: the
method's Kotlin counterpart constructs the plugin, registers it, and — for the semantics inspector —
calls `installJetWhaleSemanticsProbe()`. A pure Swift app then never sees a Kotlin plugin class.

Custom Swift plugins are the Swift SDK design's `SwiftPluginBridge`; they are out of scope for the
first package, which ships the official plugins only.

### Versioning and publishing

- `Package.swift` pins the binary target by URL and checksum to a zip on the GitHub release; the
  release workflow builds the XCFramework, zips it, computes the checksum and rewrites
  `Package.swift` in the same commit that tags the release (the KMMBridge flow, or a script).
- The Swift package version is the JetWhale version. Host and agent already have to match on the
  protocol, and a separate package version would be one more number to keep in step.
- The package lives in this repository at the root (`Package.swift` beside `settings.gradle.kts`),
  so `https://github.com/kitakkun/JetWhale` is the package URL and a release tag is a package
  version. A separate repository would decouple the tag cadence, at the cost of a second place to
  release from.
- Size: the framework carries the Kotlin runtime, Ktor, kotlinx-serialization and Compose's iOS
  runtime (the semantics agent depends on `compose-ui` for `SemanticsOwnerNodeSource`). For a
  Swift-only app the Compose dependency is dead weight, and splitting it out is a follow-up: move
  `SemanticsOwnerNodeSource` and the composable probes into a `semantics-agent-compose` module, so
  the iOS walker links without Compose.

### What has to exist before this ships

1. The Kotlin façade: `JetWhaleSwiftConfig`, `startJetWhaleFromConfig`, and per-plugin registration
   methods. Swift SDK design, items 1 and 3.
2. The umbrella module and its dynamic XCFramework build.
3. The release workflow step that publishes the zip and rewrites `Package.swift`.
4. The Compose split above, if the first consumers are Swift-only apps.

## Plan

1. **Protocol**: `AppleNode`, `NodeHitTesting`, host `NodeTree` / `NodeMcpJson` / tree view, tests. ✅
2. **Agent `iosMain`**: `IosUiThread`, `AppleNodeIds`, `AccessibilityTreeCapture`,
   `IosWindowNodeSource`, `installJetWhaleSemanticsProbe()`. ✅
3. **Actions**: the four handler objects. ✅
4. **Demo**: the SwiftUI bar in `ios_cmpApp.swift` as the iOS counterpart of the Android
   `AndroidView` samples; the probe installed from `cmpAppViewController()`. ✅
5. **Docs**: the guide's iOS section. ✅
6. **Follow-up**: Compose text entry through the first responder.
7. **Follow-up**: the Swift package, per the section above.

## Risks

- **Cost of reading `accessibilityElements`.** Compose builds its accessibility tree lazily; a
  capture forces it. Captures of the demo take about fifty milliseconds, but a long `LazyColumn`
  should be measured before the capture is wired to a periodic refresh.
- **Private class names.** `className` for SwiftUI's hosting view and the root controller is a
  mangled Swift generic name. The wire value stays raw; the host shows the last path segment.
- **SwiftUI behavior across OS versions.** `accessibilityElements` on the hosting view is public
  API, but which nodes SwiftUI chooses to expose is SwiftUI's business. The findings are from
  iOS 26.2; the demo should be run on the oldest deployment target it supports before the first
  release.
- **Roots are in registration order.** `NodeHitTesting` reads roots bottom to top, and the probe
  registers windows as it finds them, not by `windowLevel`. An app with two overlapping windows of
  its own — rare on iOS, where sheets and alerts stay inside the one window — could get the
  obstruction between them the wrong way round. The registry has no reordering, so this waits for
  a real case.
- **Element order is traversal order.** `NodeHitTesting` reads children as paint order, last on
  top, which holds for `subviews` but is only an assumption for `accessibilityElements`: a toolkit
  may order those for VoiceOver rather than by stacking. Overlapping SwiftUI or Compose siblings
  can therefore get an `obscuredBy` the wrong way round. Nothing in the protocol says which is
  drawn on top, so this is documented rather than fixed.
- **The text-entry trait bit.** Bit 18 has no public constant. If a future SwiftUI or Compose stops
  setting it, fields stop reading `editable`; the `UITextField` check still catches SwiftUI's.
