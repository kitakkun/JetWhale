# In-app screen stream: the app's own windows, captured from inside the app

Status: **Android slice implemented** in `jetwhale-plugins/screen/{protocol,agent,host}` and
measured on an emulator. iOS, Desktop and Web are designed below but not built. The verdict is at
the end.

## Why, and why not the Device Mirror

The Device Mirror work captures a device from outside, through ADB or the simulator. It sees
everything — system bars, the keyboard, other apps — and knows nothing about the app.

Capturing from inside the app inverts both: it sees only the app's own windows, and it knows
exactly what the app is doing at the moment of each frame. The two are complementary, not
competing: the mirror for "what is the whole device showing", this stream for "what is the app
showing, and why".

What only the inside view can offer, and what this design keeps room for:

1. **One clock with the app's events.** Frames carry the agent's capture time, and the host maps
   it onto its own clock, so a frame lines up with the network calls, navigation and log lines the
   other plugins record. That is the base of a later flight recorder.
2. **Frames that know their elements.** The Semantics plugin can capture the node tree of the same
   screen; the host can overlay it on a frame for hover-to-inspect and give an AI agent an image
   and a structure that agree.
3. **Masking before pixels leave the device.** An element marked with
   `Modifier.maskedInScreenStream()` is blacked out on the device, before encoding.
4. **Anywhere the agent connects.** No USB, no developer options, no `adb`: a device on the
   network, a tester's phone, and — once built — iOS devices, desktop apps and web apps.
5. **Render-driven.** A frame is captured only after the app redraws, so a still screen costs
   nothing.

## Goals and non-goals

Goals: a live view of the app's windows good enough to inspect UI state and follow interactions;
frames timestamped on one clock; on-device masking; a cost the app does not notice.

Non-goals: smooth video, the system UI or the keyboard, other apps, audio, input injection (a later
step), recording (the flight recorder is a later step built on this).

## Capture, per platform

| Platform | Capture | Limits |
|---|---|---|
| Android (built) | Every window of the process from `WindowInspector.getGlobalWindowViews()` (API 29), each copied with `PixelCopy` scaled straight into a bitmap of the target size, composited at its on-screen position, off the main thread. | API 29+. Dialogs and popups need `PixelCopy.Request.ofWindow(view)` (API 34); on 29–33 only activity windows are copied and other windows stay black. The keyboard (another process's window), system bars and dim-behind (compositor layers) never appear. A `FLAG_SECURE` window copies as black. |
| Compose (any target) | `rememberGraphicsLayer()` around the root and `GraphicsLayer.toImageBitmap()`. | Only Compose content: native views, and dialogs and popups in their own windows, are missed. Worth it where no platform capture exists. |
| iOS | `drawViewHierarchyInRect(afterScreenUpdates:)` per window for stills; ReplayKit `RPScreenRecorder.startCapture` for a real stream of the app's screen as `CMSampleBuffer`s. | ReplayKit asks the user once per session. Compose Multiplatform renders with Metal, which `drawViewHierarchy` may not capture — to be verified; the Compose capture above is the fallback. The keyboard is a separate remote window. |
| Desktop (Compose) | Skiko `SkiaLayer.screenshot()`. | `java.awt.Robot` would need the macOS screen-recording permission, so it is avoided. |
| Web (Compose wasm) | The canvas: `toBlob` for stills, `captureStream()` for video. | A DOM-rendered app has no reliable equivalent. |

## Transport and pacing

- **Format:** JPEG per frame (MJPEG). The host decodes it with Skia, which it already has; H.264
  would cut bandwidth several times over but needs a decoder on the host JVM.
- **Carrier:** the existing plugin messenger, Base64 in a JSON event. That costs a third more bytes
  than binary; the numbers below show it is affordable, and a binary channel is an optimization
  for later, not a prerequisite.
- **Flow control:** credits. The stream starts with two; each frame spends one and the host returns
  it after decoding. Without credit the agent does not capture at all, and redraws that happen
  meanwhile collapse into one capture of the latest picture — a slow link or a slow host lowers the
  frame rate instead of queueing stale frames.
- **Pacing:** a frame is captured only after a redraw (a draw listener on every window), never
  sooner than `1 / maxFramesPerSecond` after the previous one. `FramePacer` holds this logic and is
  unit-tested.
- **Clock:** the start/stop replies carry the agent's clock, and the host takes the offset from the
  midpoint of the round trip. It turned out to be necessary even on an emulator on the same
  machine, whose clock ran **4.8 s** ahead of the host's; without it, latency came out negative.

## Pairing with the semantics tree

Measured against the running Semantics plugin on the same session: capturing the tree takes
**~5 ms on the device's UI thread**, adds **~18 ms** per round trip, and returns **~9 KB** of
JSON for the demo screen. Cheap on demand; too costly for every frame (≈75 ms of UI thread a second
at 15 fps). So the tree is fetched when the view is paused or hovered, not per frame, and the frame
it belongs to is the latest one — close enough for inspection, and exact once the stream is paused.

## Masking

`Modifier.maskedInScreenStream()` records its element's on-screen bounds after each layout. The
capture snapshots those bounds on the main thread at the moment it requests the copy, and blacks
the rectangles out of the composited frame before encoding. Verified: typing "secret123" into a
masked field left the field black in the stream while the device showed the text. Two limits: a
mask is only as current as the last layout (padding the rectangle, or skipping frames while a
masked element moves, would close the gap), and masking is opt-in — everything unmasked is
streamed.

## Measurements

Android emulator `Pixel_10`, API 37 arm64, 1080×2424, on an Apple M2 Pro Mac that was also running
this emulator, the host, and two to three Gradle daemons (load average 8–11 throughout). No
physical device was available. Workload: continuous vertical swipes over a scrolling list for 8 s;
stats are the host's view of the last 5 s.

The environment turned out to matter more than any setting. The emulator was first started with
`-no-window`, which selected **SwiftShader** — software rendering on the CPU — so every GPU
read-back was a CPU copy competing with everything else on the machine. The same emulator restarted
with `-gpu host` rendered through the Mac's GPU (Metal). Both are reported.

### Where one frame's time goes (0.5× scale, JPEG quality 70)

| Stage | Where | SwiftShader | Host GPU |
|---|---|---|---|
| Main thread (find windows, positions, request copies, snapshot masks) | app, UI thread | 0.27 ms | 0.21–0.33 ms |
| `PixelCopy`, scaled into the target size | app, GPU → CPU | 31 ms | 7–10 ms |
| Compose windows + masks | app, worker | 4.6 ms | 3.1–4.5 ms |
| JPEG compress | app, worker | 6.3 ms | 5.2–6.1 ms |
| Base64 | app, worker | 0.3 ms | 0.2–0.3 ms |
| Transport (JSON serialize, WebSocket, JSON parse) | both | 17–80 ms¹ | 8.6–9.7 ms |
| Base64 + JPEG decode | host | 2.8 ms | 2.2–2.7 ms |

¹ Transport is measured across the two clocks, with one offset sample taken at stream start; under
the machine's load that sample was off by tens of milliseconds, which is why it swings between runs.

### Results per setting

| Environment | Scale / quality / cap | Frames/s | Latency avg / p95 | Bytes/frame | Bandwidth |
|---|---|---|---|---|---|
| SwiftShader | 0.25 / q50 / 30 | 17–19 | 36–114 / 60–159 ms | 11.5 KB | 195–213 KB/s |
| SwiftShader | 0.5 / q70 / 30 | 13–18 | 62–160 / 104–255 ms | 33 KB | 420–600 KB/s |
| SwiftShader | 1.0 / q70 / 30 | 4–15 | 89–253 / 137–260 ms | 85 KB | 340–1,245 KB/s |
| Host GPU | 0.25 / q50 / 30 | 26.0 | 22 / 33 ms | 11.6 KB | 303 KB/s |
| Host GPU | 0.5 / q70 / 30 | 22.8 | 33 / 60 ms | 34 KB | 785 KB/s |
| Host GPU | 0.5 / q70 / 60 | 35.8 | 27 / 39 ms | 34 KB | 1,242 KB/s |
| Host GPU | 1.0 / q70 / 30 | 23.8 | 58 / 84 ms | 85 KB | 2,021 KB/s |

- **Idle:** one frame when the stream starts, none after while nothing moves.
- **Cold start:** the first capture of a stream costs more (13 ms of main thread and an 81 ms copy
  in one run) while bitmaps and the copy path warm up.
- **Effect on the app.** `dumpsys gfxinfo` janky-frame percentages were unusable here: two runs
  without the stream gave 51 % and 76 % on the host GPU. Frame-time percentiles are steadier:
  without the stream p50 17 ms / p90 18–22 ms; with it at 0.25× and 0.5× p50 8–16 ms / p90 17–19 ms;
  at 1.0× p90 25 ms. No regression is visible up to half scale; full scale may cost a little, and
  that has to be measured on real hardware.
- **Masking:** typing "secret123" into a masked field left it black in the stream while the device
  showed the text. **Keyboard:** open on the device, absent from the stream, as expected.

### What is inherent, what is fixable

Inherent to capturing from inside the app:

- **A GPU read-back per window per frame.** `PixelCopy` is the only public way to get a window's
  pixels on Android, and it copies from GPU memory. It is cheap on a GPU (7–13 ms here, less on a
  phone's unified memory, unmeasured) and expensive in software rendering (31–77 ms). Everything
  else is small next to it.
- **Encoding on the device's CPU,** in the app's process: 5–25 ms a frame by scale, paid from the
  app's battery. Scale and quality trade it directly.
- **Nothing outside the app** — keyboard, system bars, dim-behind, `FLAG_SECURE` windows.

Fixable in this design:

- **One frame at a time.** A new capture starts only after the previous one is encoded, so the
  rate is bounded by copy + compose + compress. Overlapping the next copy with the current encode
  (the credits already allow two in flight) raises the ceiling by roughly the encode time.
- **Base64 in JSON:** +33 % bytes, and a ~45 KB string through the JSON parser on each side. It is
  a small share today (0.3 ms agent, part of ~9 ms transport); a binary frame on the messenger
  removes it and matters more over Wi-Fi.
- **JPEG instead of video:** H.264 from `MediaCodec` would cut bandwidth several times over, which
  matters for a remote or Wi-Fi device, but needs a decoder on the host JVM and an extra copy into
  the encoder's surface. Worth it only once remote use is real.
- **Allocation per frame:** bitmaps are created and recycled each frame; reusing them removes GC
  churn in the app.
- **The clock offset** should be re-sampled and the smallest round trip kept, NTP-style, rather than
  taken once at start.
- **Defaults:** half scale at quality 70 is the balance point measured here; full scale costs twice
  the encode for little a debugger needs.

Why the stream looked slow in the host window: it was measured on SwiftShader under heavy machine
load, where the read-back alone took 30–80 ms and the frame rate fell to 4–18 fps. On the host GPU
the same code ran at 23–36 fps with 22–58 ms latency.

Evidence was captured under `build/qa/` in the working tree and is not committed: host screenshots
of the stream in both environments, the masked field, the keyboard test next to a device
screenshot, and the raw numbers.

## Verdict: go, with changes

It works without ADB and the app does not notice it: a fraction of a millisecond of main thread per
frame, and nothing when idle. On a GPU-backed device it streams at 23–36 fps with 22–58 ms latency at
half scale, which is enough to follow an interaction as it happens. On software rendering it drops
to a slideshow, so the setup it runs on has to be stated. Masking works as designed. What makes it
worth having over the mirror is the in-app context, and the pieces that context needs — a shared
clock, on-demand semantics, masks — are cheap.

Changes to make before it ships:

- **Position it as a context view, not a mirror,** and say what it cannot show (keyboard, system
  UI, secure windows) in the UI.
- **Pipeline capture and encode**, and **reuse bitmaps**.
- **Semantics on demand, not per frame.**
- **Clock sync as a shared primitive** with repeated sampling, since the flight recorder and any
  cross-plugin timeline need the same offset.
- **API floor:** Android 10 for the stream at all, Android 14 for dialogs and popups.
- **Measure on physical devices,** low-end ones in particular, before promising frame rates.

First PR: this Android slice — protocol, agent (Android capture, stubs elsewhere), host live view
with per-stage stats and the `start` / `stop` / `getStats` MCP tools,
`Modifier.maskedInScreenStream()` — marked experimental, plus capture/encode pipelining. Out of it:
the flight recorder, semantics overlay, input injection, H.264 or a binary channel, and iOS /
Desktop / Web capture, each its own step.

## Risks

- `PixelCopy` cost on low-end hardware is unmeasured and bounds everything else.
- Masking is opt-in; an app that forgets a field streams it. The agent is a debug-build
  dependency, which limits the exposure, but the UI should make the stream's presence obvious.
- A `FLAG_SECURE` screen and the keyboard are invisible, which can mislead if not labelled.
