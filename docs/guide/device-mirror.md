# Device Mirror

The Device Mirror shows the live screen of an Android emulator or device, an iOS simulator, or a
physical iPhone inside the host window. You can tap, swipe, type and press hardware buttons on
the mirrored screen, and take screenshots and recordings. Captures are kept per device, so the
ones from a test run are easy to find again. An AI agent can do the same over MCP.

It is a host-only plugin: the app you debug needs no agent for it, and it appears for every
session once one is selected.

## Setup

### Install the host plugin

The Device Mirror decodes video with ffmpeg, whose native libraries are built for one operating
system at a time. It is therefore not in the official catalog. Build it from a checkout of this
repository on the machine that runs the host:

```shell
./gradlew :jetwhale-plugins:mirror:host:installPlugin
```

This copies `jetwhale-device-mirror.jar` into `~/.jetwhale/plugins/`. Restart the host to load it.

### Tools on your machine

The plugin drives the command-line tools you already use for devices. It looks for them on
`PATH`, and also in Homebrew's `/opt/homebrew/bin` and `/usr/local/bin`, because an app started
from the Dock does not inherit your shell's `PATH`.

| To mirror | You need |
|-----------|----------|
| Android emulators and devices | `adb` from the Android SDK platform-tools |
| iOS simulators (macOS) | Xcode's `xcrun simctl`, plus [idb](https://fbidb.io): `brew install facebook/fb/idb-companion` and `pip3 install fb-idb` |
| iPhones connected by USB (macOS) | idb as above |

When a tool is missing, the device list says which one and what it would enable.

## What you get in the host

- **Devices.** Android and iOS devices in two groups, refreshed every few seconds. A device
  appears once it is booted or connected.
- **Live view.** The mirrored screen fills the pane at its own aspect ratio, and the video is
  decoded at the size it is shown. A click is a tap and a drag is a swipe, at the matching point
  on the device.
- **Hardware buttons.** Android has Home, Back, Power, Volume up and Volume down; a simulator has
  Home and Power.
- **Text.** The field under the screen types into whatever has focus on the device. On Android,
  text with a line break is refused; type each line separately.
- **Screen off and Wake (Android).** A device whose screen is off sends nothing, so the live view
  says so and offers **Wake**, which turns the screen on and lifts a lock screen that has no PIN,
  pattern or password. The toolbar has **Screen off** or **Wake** after the hardware buttons.
  iOS devices have neither.
- **Screenshot and Record.** Both save into the device's captures (below). Only one recording
  runs at a time. Android stops recording on its own after 180 seconds.
- **Stats.** The line under the screen shows frames received and shown per second, and how long
  reading and decoding, copying and drawing each take.

### A physical iPhone is view-only

idb can show an iPhone's screen and take screenshots of it, but it cannot send touches, buttons
or text to a real device. For an iPhone the mirror therefore shows **View only**: screenshots
work, while input and recording do not.

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

- the device's id, name, platform, kind and OS version;
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
  in Finder or Explorer, **Copy image** puts a screenshot on the clipboard, **Copy path** copies
  its absolute path, and **Delete…** removes it after you confirm.
- **Open folder** opens the device's folder, or the captures folder when all devices are shown.

Thumbnails are small PNGs cached in a hidden `.thumbnails` folder beside the captures. Only the
most recently shown ones are kept in memory.

## MCP tools

With the [MCP server](./mcp-server) running, the same operations are available to an AI agent.
Each tool takes the `sessionId` of any active session, like other plugin tools. The `deviceId`
can be left out to use the device selected in the mirror.

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.mirror.listDevices` | The devices, with their ids, platform, kind, OS version, and what they accept: input, buttons, recording. An Android device also reports `screenOn` and `locked` |
| `com.kitakkun.jetwhale.mirror.captureScreenshot` | Saves a screenshot among the device's captures; returns its path and size in pixels |
| `com.kitakkun.jetwhale.mirror.tap` | Taps at a point, in the pixels of a screenshot |
| `com.kitakkun.jetwhale.mirror.swipe` | Swipes between two points over a duration |
| `com.kitakkun.jetwhale.mirror.pressButton` | Presses a hardware button the device has |
| `com.kitakkun.jetwhale.mirror.inputText` | Types text into the focused field |
| `com.kitakkun.jetwhale.mirror.setScreen` | Turns an Android device's screen on (`on: true`, also lifting a lock screen without a credential) or off; returns `screenOn` and `locked` afterwards, where `locked: true` means the device still needs unlocking |
| `com.kitakkun.jetwhale.mirror.startRecording` | Starts recording the screen |
| `com.kitakkun.jetwhale.mirror.stopRecording` | Stops it; returns the video's path and length |
| `com.kitakkun.jetwhale.mirror.listCaptures` | Saved captures, newest first, optionally only one `deviceId`, one `kind` (`Screenshot` or `Recording`), or those taken at or after `since` (epoch milliseconds) |

An agent can read a returned path to look at the capture. On a physical iPhone, input and
recording are refused with the reason.
