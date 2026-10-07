# Device Mirror <Badge type="warning" text="experimental" />

The Device Mirror shows the live screen of an Android emulator or device, an iOS simulator, or a
physical iPhone inside the host window. On Android devices and simulators you can tap, swipe,
type and press hardware buttons on the mirrored screen; an iPhone is view-only. Every device can
take screenshots and recordings. Captures are kept per device, so the
ones from a test run are easy to find again. An AI agent can do the same over MCP.

It needs no app: nothing is added to the app you debug, and it is listed at the top of the
sidebar, above the app picker, usable as soon as the host starts with nothing connected — see
[Plugins that need no app](/guide/host-window#plugins-that-need-no-app).

## Setup

### Install the host plugin

Install **Device Mirror** from **Settings → Plugins → Add Plugins → Official Plugins**. To install
it by Maven coordinates instead, use `com.kitakkun.jetwhale:jetwhale-device-mirror:<version>`,
released with the host under the host's version. The app needs nothing added.

### Tools on your machine

The plugin drives the command-line tools you already use for devices. It looks for them on
`PATH`, and also in Homebrew's `/opt/homebrew/bin` and `/usr/local/bin`, because an app started
from the Dock does not inherit your shell's `PATH`.

| To mirror | You need |
|-----------|----------|
| Android emulators and devices | `adb` from the Android SDK platform-tools |
| iOS simulators (macOS) | Xcode's `xcrun simctl`, plus [idb](https://fbidb.io): `brew install facebook/fb/idb`, which installs the command-line client and its companion |
| iPhones connected by USB (macOS) | idb as above |
| Live video from Android devices and iPhones | [ffmpeg](https://ffmpeg.org): `brew install ffmpeg`, `winget install ffmpeg` or `apt install ffmpeg` |

Android devices and iPhones send their screen as H.264, which the plugin decodes by running the
`ffmpeg` command. Without it an Android device is shown through screenshots, a few times a second,
and an iPhone cannot be mirrored. iOS simulators, and Android emulators that expose their own gRPC
screen stream, send their screen without ffmpeg; an emulator without that stream is decoded like a
device, and so is a foldable emulator folded through `adb shell cmd device_state state`, whose own
stream follows only the emulator's fold control.

When a tool is missing, the device list says which one and what it would enable.

## What you get in the host

- **Device picker.** The device's name at the top left opens every device, Android and iOS in
  two groups, with its kind, its OS version (iOS only) and a dot for what the mirror last saw:
  showing its screen, screen off, unavailable, or (hollow) not watched yet. The list refreshes
  every few seconds; a device appears once it is booted or connected. The picker's first entry, **All
  devices**, opens the grid.
- **All devices (grid).** Every device as a tile with a screenshot refreshed every 1.5 seconds,
  its status dot, name, kind, OS version (iOS only), and how long ago the picture was taken;
  **Screen off** or **Unavailable** (with the reason) says why a picture is old. The tiles are sized so that all
  of them fit the pane and are centered in it, each at its device's own shape; with more devices
  than fit at a readable size they scroll. The device open in the single view has an accent
  outline. Only tiles on screen are captured, two captures at a time at most, and nothing streams
  meanwhile. Tiles are view-only: click one, or press Enter on it, to open that device. Hovering a
  tile offers **Open** and **Screenshot** for that device, and the camera button in the toolbar
  saves one screenshot per device into the captures; what was saved shows briefly at the bottom.
  The record button next to it starts recording every device that can record; while any device
  records it turns into **Stop all**, with a count of the devices recording. Until you tick
  **Don't show again**, it asks before starting, because recording several devices at once is heavy
  on this machine — iOS simulators most, since they encode their video on the Mac.
- **Live view.** The mirrored screen fills the pane at its own aspect ratio, and the video is
  decoded at the size it is shown. A click is a tap and a drag is a swipe, at the matching point
  on the device. When an Android device rotates or a foldable folds, the view follows within a
  couple of seconds, keeping the last picture up meanwhile. An iOS simulator streams uncompressed
  frames at up to 60 per second, scaled by the simulator to the size shown. When no live video is
  available, the mirror falls back to screenshots and says why above the text field. It tries the
  video again after 5 seconds, then 15, then once a minute, and returns to it as soon as the video
  comes back.
- **Switching devices.** Switching back to a device shows its last frame at once, dimmed, until
  its stream reconnects. The last frames of the four devices shown most recently are kept.
- **Hardware buttons.** Icon buttons in the toolbar, with the name in a tooltip, in groups
  (navigation, volume, screen power, captures) that wrap to another line as a whole in a narrow
  window, so every button stays visible. Record, Screenshot and Captures are icons at the right
  end; while a recording runs, Record turns into a red counter of the time recorded, and clicking
  it stops the recording. Android has Home,
  Back, Recent apps, Power, Volume up and Volume down; a simulator has Home, Recent apps (two
  quick presses of Home, which open its app switcher) and Power, and shows its volume buttons
  disabled because idb cannot press them.
- **Text.** The field under the screen types into whatever has focus on the device. On Android,
  text with a line break is refused; type each line separately.
- **Screen off and Wake (Android).** A device whose screen is off sends nothing, so the live view
  says so and offers **Wake**, which turns the screen on and lifts a lock screen that has no PIN,
  pattern or password. The toolbar has **Screen off** or **Wake** after the hardware buttons.
  iOS devices have neither.
- **Screenshot and Record.** Both save into the device's captures (below). Each device records
  on its own, so several can record at once. Android stops recording on its own after 180 seconds.
  The notice that names the saved file offers **Open**, which shows it in the Captures panel, and
  **Copy**, which puts it on the clipboard as in the panel.
- **Stats.** **Stats** under the screen shows frames received and shown per second, the longest
  gap between two shown frames, and how long decoding, copying and drawing each take. Time spent
  waiting for a still screen, which sends nothing, is not counted as decoding.

### A physical iPhone is view-only

idb can show an iPhone's screen, but it cannot send touches, buttons or text to a real device.
For an iPhone the mirror therefore shows **View only**: input does not work, while screenshots and
recordings, both taken from its video stream, do. Like the live view, both need ffmpeg.

If an iPhone stays black:

- Unlock the iPhone and keep its screen on.
- Allow **Camera** access for the app that runs JetWhale (the host, or the terminal or IDE that
  launched it) in **System Settings → Privacy & Security → Camera**. macOS delivers a USB
  device's screen as a camera, and without that permission it delivers nothing.
- Check that the iPhone is connected by USB and trusts this Mac.

The mirror lists the same hints when no picture arrives.

## Captures

Every screenshot and recording is kept in a folder for its device, with one folder per day:

```text
captures/
  Pixel-9-3fa2c1d0/
    2026-09-25/
      123005-screenshot.png
      123005-screenshot.png.json
      123412-recording.mp4
      123412-recording.mp4.json
  iPhone-17-9b04e7aa/
    …
```

The short code after the device name comes from the device's serial number or UDID. A device
therefore keeps its folder across sessions, and two devices with the same name get separate
folders. The `.json` file beside each capture records:

- the device's id, name, platform, kind and, for an iOS device or simulator, its OS version;
- the capture's size in pixels;
- when it was taken;
- for a recording, how long it runs.

The list is rebuilt from these files, so captures from earlier sessions stay listed. A capture
that you delete in Finder disappears from the list.

The folder defaults to `~/.jetwhale/plugin-data/com.kitakkun.jetwhale.mirror/captures`. **Change
folder…** in the Captures panel picks another folder, and the host remembers it.

**Captures** in the toolbar opens the panel beside the live view:

- **This device / All devices** and **All kinds / Screenshot / Recording** filter the list, and
  the date tags narrow it to one day.
- Captures are grouped by day, newest first, and a new capture appears as soon as it is saved.
- Selecting a capture shows its details. **Open** opens it in its default app, **Reveal** shows it
  in Finder or Explorer, and **Delete…** removes it after you confirm. **Copy image** puts a
  screenshot on the clipboard both as an image and as its file; **Copy file** puts a recording
  there as its file, which Finder, chat apps and upload fields accept. **Copy path** copies its
  absolute path.
- **Open folder** opens the device's folder, or the captures folder when all devices are shown.

Thumbnails are small PNGs cached in a hidden `.thumbnails` folder beside the captures. Only the
most recently shown ones are kept in memory.

## MCP tools

With the [MCP server](./mcp-server) running, the same operations are available to an AI agent.
The tools live in the `host` session, which `jetwhale.listSessions` always lists first, so pass
`host` as their `sessionId`. The `deviceId` can be left out to use the device selected in the
mirror.

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.mirror.listDevices` | The devices, with their ids, platform, kind, and what they accept: input, buttons, recording. An iOS device or simulator also reports `osVersion`; an Android device reports `screenOn` and `locked` |
| `com.kitakkun.jetwhale.mirror.captureScreenshot` | Saves a screenshot among the device's captures; returns its path and size in pixels |
| `com.kitakkun.jetwhale.mirror.tap` | Taps at a point, in the pixels of a screenshot |
| `com.kitakkun.jetwhale.mirror.swipe` | Swipes between two points over a duration |
| `com.kitakkun.jetwhale.mirror.pressButton` | Presses a hardware button the device has |
| `com.kitakkun.jetwhale.mirror.inputText` | Types text into the focused field |
| `com.kitakkun.jetwhale.mirror.setScreen` | Turns an Android device's screen on (`on: true`, also lifting a lock screen without a credential) or off; returns `screenOn` and `locked` afterwards, where `locked: true` means the device still needs unlocking |
| `com.kitakkun.jetwhale.mirror.startRecording` | Starts recording one device's screen, several devices' (`deviceIds`), or every device that can record (`all: true`) |
| `com.kitakkun.jetwhale.mirror.stopRecording` | Stops one recording, several (`deviceIds`), or all of them (`all: true`); returns each video's path and length |
| `com.kitakkun.jetwhale.mirror.listCaptures` | Saved captures, newest first, optionally only one `deviceId`, one `kind` (`Screenshot` or `Recording`), or those taken at or after `since` (epoch milliseconds) |

An agent can read a returned path to look at the capture. On a physical iPhone, input is
refused with the reason.
