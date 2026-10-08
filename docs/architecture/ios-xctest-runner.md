# iOS input through an XCTest runner

Status: **proposed**. Checked on an iOS simulator only; nothing here has run on a physical device.

## Goal

Drive iOS UI the way WebDriverAgent does: through a small XCUITest runner that the host starts on
the simulator or device and sends commands to. XCTest can inject touches, swipes, text and hardware
buttons anywhere on the screen, and read the element tree of **any** app, SpringBoard and its system
alerts included. That would give:

- **Device Mirror** real input on physical iPhones, which are view-only today because idb cannot
  send touches to a device, and an input path on simulators that does not depend on idb;
- **Compose Semantics** a node source for apps without the JetWhale agent, and for system UI the
  in-process agent cannot see (permission prompts, SpringBoard, other apps);
- **MCP** the same reach for agents.

## What was checked on a simulator

Xcode 27.0 (27A266a), a new iPhone 17 simulator on iOS 26.2. The runner was a UI-test bundle with
an empty host app; its one test started an HTTP server on `127.0.0.1` (Network.framework) and spun
the main run loop, executing each command on the main thread. The targets were a scratch SwiftUI
app (a counter button, a text field, a long-press area, a scrolling list, notification and location
prompts), Settings and SpringBoard.

```bash
xcrun simctl create "JetWhale XCTest Check" com.apple.CoreSimulator.SimDeviceType.iPhone-17 com.apple.CoreSimulator.SimRuntime.iOS-26-2
xcodebuild build-for-testing -project XCTestCheck.xcodeproj -scheme Runner \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath dd
TEST_RUNNER_JETWHALE_RUNNER_PORT=18100 xcodebuild test-without-building \
  -xctestrun dd/Build/Products/Runner_iphonesimulator27.0-arm64.xctestrun \
  -destination "platform=iOS Simulator,id=$UDID" -only-testing:RunnerUITests/RunnerTests/testServe
curl -X POST http://127.0.0.1:18100/tap -d '{"x": 201, "y": 120}'
```

Each effect was confirmed by reading the app's element tree back, and by simulator screenshots.
Latencies are client round trips over a kept-alive connection, median / 90th percentile.

| Command | XCTest API | Result | Latency (n) |
|---|---|---|---|
| Tap at a point | `XCUICoordinate.tap()` on SpringBoard's element | 30 of 30 registered | 414 / 431 ms (31) |
| Tap at a point | private `XCSynthesizedEventRecord` | 30 of 30 registered | 292 / 298 ms (30) |
| Long press, 1 s | `XCUICoordinate.press(forDuration:)` | `onLongPressGesture` fired | 1346 / 1358 ms (5) |
| Swipe, 0.3 s | `press(forDuration:thenDragTo:withVelocity:thenHoldForDuration:)` | list scrolled | 2722 / 2762 ms (20) |
| Swipe, 0.3 s | private event record | list scrolled; home-screen pages turned | 542 / 547 ms (20) |
| Type text | `XCUIApplication(bundleIdentifier:).typeText` | 30 of 30 characters | 332 / 354 ms (11) |
| Type text | private text-input event path | 30 of 30 characters, into whatever has focus | 255 / 266 ms (10) |
| Home | `XCUIDevice.shared.press(.home)` | home screen shown | 467 / 473 ms (6) |
| Lock | private `-[XCUIDevice pressLockButton]` | lock screen shown | |
| Open an app | `XCUIApplication(bundleIdentifier:).activate()` | foreground | 43 / 522 ms (9); `launch()` 2–3 s |
| App's tree | `XCUIApplication.snapshot()` | 54 nodes, frames, identifiers | 41 / 52 ms (10) |
| SpringBoard's tree | same | 219 nodes, icons with frames | 83 / 89 ms (10) |
| Settings' tree | same | 125 nodes; the first read after activation took 400 ms | 68 / 129 ms (5) |
| System alert | `springboard.alerts.firstMatch` | notification and location prompts, buttons with frames | 26 / 29 ms (10) |
| Answer the alert | tap the button by label / by coordinate | permission recorded by the app | 586 ms / 294 ms |
| Screenshot | `XCUIScreen.main.screenshot()` | 1206×2622 PNG, 290 KB | 93 / 110 ms (10) |

Starting the runner with `test-without-building` took 2.7–3.5 s to the first answered command, and a
cold `build-for-testing` 11 s. A `/shutdown` command ends the test, which passes, and `xcodebuild`
exits within a second. Killing `xcodebuild` with SIGTERM or SIGKILL took the runner app down with it
within three seconds; nothing was left running in the simulator. A runner left alone for 45 minutes
still answered and still drove the app, past the 600 s allowance its `.xctestrun` names (see Risks);
the first tap after that pause did not reach the app, and the three after it did. Two runners with
different bundle IDs ran side by side on the one simulator.

What did not work, or needed care:

- **A failed XCTest call ends the server.** `springboard.typeText` failed with "Neither element nor
  any descendant has keyboard focus", XCTest recorded the failure, the test ended and the server went
  with it. The runner overrides `XCTestCase.record(_:)` to turn an issue into the command's error, and
  catches Objective-C exceptions; after that, failing commands came back as errors and the runner
  stayed up.
- **Reading a suspended app hangs.** A snapshot of an app sent to the background waited 30 s, and
  XCTest retried twice. The runner refuses to read an app that is not in the foreground.
- **Public gestures wait for the app to idle.** A swipe through `XCUICoordinate` spends about two
  seconds waiting for the scrolled list to settle. The private event record does not wait, which is
  what WebDriverAgent uses for its actions too.
- **Text needs the focused app.** `typeText` works only on the app that holds keyboard focus, and
  XCTest has no public way to ask which app that is. The private text-input event types into
  whatever has focus, like `idb ui text`.
- **Recent apps.** Two `press(.home)` calls land 940 ms apart, too slow for the app switcher, and a
  synthesized swipe up from the bottom edge scrolled the app instead. No command opens it yet.
- **Volume** buttons are unavailable in the Simulator by declaration
  (`XCUIDeviceButtonVolumeUp XCUI_SIMULATOR_UNAVAILABLE`); they exist on devices.
- **idb no longer sends input with Xcode 27.** On the same simulator, `idb ui tap` failed: "SimulatorKit
  is required for HID interactions: … `/Applications/Xcode.app/Contents/Developer/Library/PrivateFrameworks/SimulatorKit.framework`
  … does not exist". Xcode 27 ships SimulatorKit in `Contents/SharedFrameworks`, and idb_companion
  1.1.8, the current Homebrew release, dates from 2022. The mirror's simulator input therefore does
  nothing with Xcode 27 today.
- **The runner's own screen is wrong.** The runner process's `UIScreen` reported a 320×480-point
  screen on the iPhone 17; an `XCUIScreen` screenshot has the real 1206×2622 pixels at 3×.
- **Kotlin/Native in the runner.** A static framework built with `kotlinc-native` 2.4.10 for
  `ios_simulator_arm64` linked into the UI-test bundle, and its function ran inside
  `RunnerUITests-Runner`. Transport (b) below is therefore possible; it is not chosen for other
  reasons.

### Coordinates

- Element frames, and offsets given to `XCUICoordinate`, are in **points in the interface
  orientation**: in landscape, the app's frame was 874×402 and its button's frame moved accordingly.
  `simctl io screenshot` follows the same orientation (2622×1206 in landscape).
- SpringBoard's own frame stays portrait (402×874), yet a coordinate anchored at it took
  interface-orientation points and hit the button in both landscape orientations.
- The private event record takes **device-native portrait points**, whatever orientation it is
  given; landscape points missed. `XCUICoordinate.screenPoint` converts an interface point to that
  space (74 ms), and a private tap at the converted point hit in both landscape orientations.
- Pixels to points is the screen scale: 3 on this iPhone (1206 px / 402 pt).

## Design

### Where it lives

The runner belongs to no single plugin. Device Mirror is its first user, for input; Compose
Semantics is the next, for system UI and for apps without the agent. Both must use it without
depending on each other, so it is a module of its own:

```
jetwhale-plugins/ios-xctest-runner/
├─ runner/   the Xcode project: an empty host app and the UI-test bundle, Swift plus about 60
│            lines of Objective-C for the private event API and for catching exceptions
└─ client/   a Kotlin library a plugin bundles: it builds, starts and talks to runners
```

The client carries the Xcode project in its jar. On first use it builds the project with the
user's own Xcode (`build-for-testing`), keyed by Xcode's build version and the project's hash, and
per team and device for a device. Building on the user's machine keeps the private-API use in step
with the XCTest it runs against.

### Shared through the OS

Plugin jars load in separate class loaders and share no objects, so runners are shared the way an
idb companion is, through files under the host's app data (`<app data>/xctest-runner/`):

- **One runner per device**: an `xcodebuild test-without-building` running the runner's one test,
  `testServe`, which serves commands on the main run loop.
- **`runners/<udid>.json`** records the runner: its protocol version, `xcodebuild`'s pid, its port,
  the token every request must carry, and the team a device's runner is signed for. Only its owner
  can read it.
- **`runners/<udid>.lock`**: whoever starts or replaces a device's runner holds this file lock, so
  two plugins never start two runners for one device. A JVM refuses a second lock on a file it
  already locks, from any class loader, so the client treats that refusal as "held" and waits.
- **Attach or start.** The client reads the record, checks the pid and asks the runner for its
  status. A runner that answers, with this client's protocol version or a newer one, is used as it
  is: commands are only ever added. An older one, or one signed for another team, is told to shut
  down and replaced. A record whose runner does not answer is dropped; nothing is killed on the
  strength of a pid alone.
- **Idle stop.** The runner ends its test after five minutes without a command, so it never
  outlives the hosts that use it for long. The host that started a runner also ends it when it
  exits, and its `iproxy` forward with it.

### Transport: the runner listens, the client connects

Option (a), as WebDriverAgent does. The runner binds `127.0.0.1:<port>`; the client picks the port
and a random token and passes both, with the idle timeout, through `TEST_RUNNER_`-prefixed
environment variables, which `xcodebuild` hands to the runner process.

- On a simulator, the runner's loopback is the Mac's: nothing to forward.
- On a device, usbmux forwards a local port to the device's loopback over USB (`iproxy
  <local>:<device> --udid <udid>`). No Wi-Fi, no Local Network prompt for the runner, and no TLS,
  since the traffic never leaves the cable.
- The plugins that use it are host-side, and Device Mirror needs no app. A connection the client
  opens keeps the runner out of the session model.

Option (b), the runner connecting out to the host as another kind of session, would reuse the
agent's discovery and wss, and the Kotlin/Native check above says it could link. It would also need
the device on the same network as the Mac, a Local Network permission someone must accept on the
device, and a new session kind that is not an app. It is kept for later, if the runner ever needs
to push events (an alert appearing) rather than answer requests.

### Commands

HTTP/1.1 `POST` with a JSON body, one request per command, the connection kept alive. Points are
device-native: portrait, whatever the interface orientation, which is what the private event record
takes and what the mirror's screen size is in.

| Command | Body | Phase |
|---|---|---|
| `/status` | → protocol version, screen in pixels and its scale, whether private synthesis is there | 1 |
| `/tap`, `/longPress` | `x`, `y`, `durationMillis` | 1 |
| `/swipe` | `fromX`, `fromY`, `toX`, `toY`, `durationMillis` | 1 |
| `/typeText` | `text` | 1 |
| `/pressButton` | `home`, `lock`, `volumeUp`, `volumeDown` | 1 |
| `/activateApp` | `bundleId` | 1 |
| `/shutdown` | — | 1 |
| `/tree` | `bundleId` (foreground app or SpringBoard) → element tree with frames | 2 |
| `/alert` | — → SpringBoard's alert, its label and buttons | 2 |

Taps, swipes and text use the private event record. If a future Xcode drops it, `/status` says so,
taps and swipes fall back to `XCUICoordinate` anchored at SpringBoard, and text entry refuses.

### The client's API

Small, and free of any plugin's types:

```kotlin
interface XcTestRunners {
    fun refusalFor(target: XcTestRunnerTarget): String?          // no team, no iproxy: known without trying
    suspend fun runnerFor(target: XcTestRunnerTarget): XcTestRunner
    fun startInBackground(target: XcTestRunnerTarget)
    companion object { fun onThisMac(stateDirectory, xcrunPath, iproxyPath, settings): XcTestRunners }
}
interface XcTestRunner {
    val screen: XcTestRunnerScreen                                 // pixels and scale
    suspend fun tap(x: Double, y: Double)
    suspend fun longPress(…); suspend fun swipe(…); suspend fun typeText(text: String)
    suspend fun pressButton(button: XcTestRunnerButton); suspend fun activateApp(bundleId: String)
}
```

A target is a simulator or a device by UDID; the signing team comes from `XcTestRunnerSettings`,
given when the runners are made, not with each call. A runner that went away between commands is
replaced and the command sent once more. If the host later takes over starting, stopping and
settings as a service it hands to plugins, it implements `XcTestRunners`, and its callers do not
change.

## How it plugs into JetWhale

### Device Mirror (phase 1)

- **Simulators:** the runner replaces idb for input. idb's input no longer works with Xcode 27, its
  last release is from 2022, and XCTest is Apple's supported automation API, released with each
  Xcode. idb stays for the live stream, for input when the runner cannot be built or started, and
  for Recent apps, which no runner command opens. Buttons: Home and Power (lock); the Simulator has
  no volume buttons to press.
- **Devices:** with a development team set under the mirror, input stops being view-only: taps,
  swipes, text, Home, Power and volume. Every failure is a notice: no team or no `iproxy` in the
  device's banner, and signing, Developer Mode, UI Automation or a locked device, read from
  `xcodebuild`'s output, when input is tried.
- **MCP:** the existing `tap`, `swipe`, `pressButton` and `inputText` tools go through the runner;
  `listDevices` reports `input`, and `inputUnavailableReason` when there is none.

### Compose Semantics (phase 2)

An XCTest node source for the host session, through the same client: one root per app, read with
`/tree`, for the foreground app and for SpringBoard. A snapshot maps onto an `apple` node:
`elementType` as the class, `identifier` as `accessibilityIdentifier`, `label` as `text`, `value` as
`accessibilityValue`, the frame as bounds in points, plus `enabled`, `selected` and `hasFocus`. It
has no action list and no traits, so actions become coordinate gestures: `Click` taps the center,
`SetText` taps and types, `ScrollBy` drags. The in-process agent stays the richer source when the
app runs it; the XCTest source covers apps without it, other apps, and system alerts.

### MCP

Phase 1 needs no new tools. Phase 2 serves the semantics tools from XCTest roots, and adds
`activateApp` and alert handling where the semantics tools do not cover them.

## Physical devices

Not tried. What a device needs, from Apple's documentation and the Appium XCUITest driver's, which
runs WebDriverAgent the same way:

1. **A development team and a provisioning profile for the runner.** "Apple requires all apps to have
   a valid provisioning profile before they can be installed, which means that the WDA app must first
   be signed and linked to a development team"
   ([Appium: provisioning profile](https://appium.github.io/appium-xcuitest-driver/latest/getting-started/provisioning-profile/)).
   `xcodebuild` adds `.xctrunner` to the UI-test bundle's identifier, and the profile must cover it
   ([Appium: automatic configuration](https://appium.github.io/appium-xcuitest-driver/latest/getting-started/provisioning-profile/auto-config/)).
   A free account cannot create a wildcard profile, so the runner needs a bundle ID unique to that
   team ([Appium: basic manual configuration](https://appium.github.io/appium-xcuitest-driver/latest/getting-started/provisioning-profile/basic-manual-config/)).
   `xcodebuild -allowProvisioningUpdates` lets automatic signing create the profile, and
   `-allowProvisioningDeviceRegistration` registers the device (`man xcodebuild`); Xcode must be
   signed in to the account.
2. **A trusted, connected device** that accepted "Trust This Computer"
   ([Appium: device setup](https://appium.github.io/appium-xcuitest-driver/latest/getting-started/device-setup/)).
3. **Developer Mode on** (iOS 16 and later), under Settings → Privacy & Security, with a restart
   ([Apple: Enabling Developer Mode on a device](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)).
4. **UI Automation on**: Settings → Developer → Enable UI Automation
   ([Appium: device setup](https://appium.github.io/appium-xcuitest-driver/latest/getting-started/device-setup/)).
5. **A network connection on the device**: "Since iOS 16, Apple requires a device to have a live
   internet connection for validating the code signing"
   ([Appium: provisioning profile](https://appium.github.io/appium-xcuitest-driver/latest/getting-started/provisioning-profile/)).
6. **`xcodebuild` with a device destination**, `-destination 'platform=iOS,id=<udid>'`, for both
   `build-for-testing` and `test-without-building` (`man xcodebuild`).
7. **Port forwarding over USB**: `iproxy` from libimobiledevice (`iproxy <local>:<device>`; older
   releases take `iproxy <local> <device>`), as Appium documents for a self-managed WebDriverAgent
   ([Appium: manage WDA by yourself](https://appium.github.io/appium-xcuitest-driver/latest/guides/wda-custom-server/)).
   CI keychains must be unlocked before signing, per the same page.

## Risks

- **The runner lives only while its test runs.** Anything that ends the test ends the server: a
  recorded issue (handled above), a crash, the idle stop, Xcode or `testmanagerd` going away. The
  client starts a new one on the next command rather than trusting it to stay up.
- **Timeouts.** The generated `.xctestrun` carries `DefaultTestExecutionTimeAllowance = 600`, but
  `xcodebuild` enforces execution allowances only with `-test-timeouts-enabled YES`
  (`man xcodebuild`); the runner here ran past that. Individual XCTest queries still time out after
  30 s, which is what the suspended-app guard avoids.
- **Private API drift.** `XCSynthesizedEventRecord`, `XCPointerEventPath`, `-[XCUIDevice
  eventSynthesizer]` and `pressLockButton` are present in Xcode 27's `XCUIAutomation.framework` and
  are what WebDriverAgent relies on. They can change in any Xcode; the fallback above keeps taps and
  swipes working through public API.
- **Xcode version drift.** The `.xctestrun` name embeds the SDK version and the build depends on the
  installed Xcode, so the cache is keyed by Xcode's build version and rebuilt after an update.
- **Other UI tests on the same device.** Two runners ran side by side here, but a developer's own UI
  tests on the device the mirror drives would still compete with it for the screen. The idle stop
  ends the runner when nothing uses it.
- **Landscape.** Points are device-native; a landscape screenshot's coordinates have to be rotated
  first. `XCUICoordinate.screenPoint` does that conversion, at 74 ms a point; the mirror does not use
  it yet.
- **Loopback is shared.** Any local process can reach a loopback port; the per-run token keeps them
  from driving the device.
- **Devices are unverified**: signing, Developer Mode, UI Automation, a locked device and `iproxy`
  are taken from documentation, not from a run.

## Phased plan

1. **The runner module, and Device Mirror input through it.** `jetwhale-plugins/ios-xctest-runner/`
   with its client; Device Mirror as the first user; simulators verified end to end, devices
   experimental.
2. **XCTest node source** for Compose Semantics, with SpringBoard and system alerts, through the
   existing semantics MCP tools.
3. **App control and alerts over MCP**: activate, launch and terminate apps, read and answer system
   alerts directly.

## Open decisions

- **Build on first use, or ship a prebuilt simulator runner.** Building needs Xcode, which a
  simulator user has anyway, and takes about 11 s once per Xcode version; a prebuilt one would have
  to match each Xcode's XCTest.
- **Private event synthesis.** Taps are 30 % faster and swipes five times faster than through public
  API, and text entry needs it. The alternative is public API only, with slower swipes and text that
  must name the target app.
- **Recent apps on simulators**: it still goes through idb, which no longer sends input with Xcode 27.
  Drop the button, or find a gesture that opens the switcher.
- **A host-provided runner service**: whether starting, stopping and the signing team move from the
  plugins into the host, which would hand plugins an `XcTestRunners`.
- **The runner's bundle ID on devices**: derived from the team ID, as now, or set by the user.
- **The idle timeout**: five minutes now; long enough not to restart between bursts of input, short
  enough not to hold a UI-test session on an unwatched device.
