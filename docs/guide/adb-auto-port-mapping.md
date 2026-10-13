# ADB Auto Port Mapping

When you debug an **Android device or emulator**, the debuggee app connects to the JetWhale host's
WebSocket server on `localhost`. For that to work, the device needs an
[`adb reverse`](https://developer.android.com/tools/adb) port forwarding so that `localhost:<port>`
on the device reaches the host machine.

JetWhale manages this for you: with **ADB auto port mapping** enabled, the host watches ADB for
device connections and sets up (and tears down) the reverse port forwarding automatically — no
manual `adb reverse` commands needed.

## Enabling it

It is **on by default** — Android debugging needs no setup. The toggle lives under
[**Settings → Connection → ADB Support**](/guide/host-settings#adb-support) if you want to turn it
off. The debug server reads the setting only when it starts, so a change takes effect the next time
the server is restarted (Apply on the Debug Server page, `jetwhale.restartDebugServer`, or a host
restart).

While enabled, the host:

1. Locates your `adb` binary automatically (see [How adb is found](#how-adb-is-found)).
2. Runs `adb track-devices` to watch devices connect and disconnect.
3. When a device comes online, runs the equivalent of:

   ```shell
   adb -s <serial> reverse tcp:<serverPort> tcp:<serverPort>
   ```

   for **each** active server port — the plain ws port and, while wss is enabled, the
   [wss](/guide/connecting#secure-connections-wss) port too — so both endpoints reach the host.
4. When a device goes offline, removes its reverse mappings.

If the ADB server restarts or crashes (for example, another tool ran `adb kill-server`), JetWhale
automatically re-attaches to the device tracking stream two seconds later, and keeps retrying — you
don't need to toggle the setting again.

Stopping the debug server stops the device tracking and removes all reverse mappings JetWhale
created. Turning the setting off does not, on its own, tear anything down: it only decides whether
the *next* server start wires anything up.

## How adb is found

JetWhale looks for the `adb` executable in these locations, in order:

- `$ANDROID_HOME/platform-tools` and `$ANDROID_SDK_ROOT/platform-tools`
- The default Android SDK location — `$HOME/Android/Sdk/platform-tools` on Linux,
  `$HOME/Library/Android/sdk/platform-tools` on macOS, `%LOCALAPPDATA%\Android\Sdk\platform-tools`
  on Windows
- Each directory on the host's own `PATH`
- `/opt/homebrew/bin` and `/usr/local/bin`, Homebrew's on Apple silicon and on Intel Macs, where its
  `android-platform-tools` cask puts adb (macOS and Linux)
- Only when none of the above has adb, each directory on the `PATH` your login shell sets up (macOS
  and Linux)

An app started from Finder, the Dock or a desktop entry does not inherit your shell's `PATH`, so when
the other locations have no adb, the host reads the `PATH` your login shell sets up. It runs your
shell (`$SHELL`, or `/bin/zsh` on macOS and `/bin/sh` elsewhere when it is unset) as an interactive
login shell, as a terminal does, at most once per run, the first time it looks for adb. The shell
runs your startup files, `.zshrc` and `.bashrc` included, so whatever they start runs again. When the
shell has not answered within 5 seconds, exits with an error or prints no `PATH`, adb is not found.
On Windows an app gets your `PATH` however it is started, so no shell is run.

If your startup files live in a folder macOS protects, such as Documents, Desktop or iCloud Drive,
macOS asks whether JetWhale Debugger may access that folder when the shell starts. If you choose
**Don't Allow**, the shell cannot read those files, and what they add to `PATH` is not searched.
Either keep adb where the host finds it without the shell — in the SDK's default location, where
Android Studio installs it, or through Homebrew with `brew install --cask android-platform-tools` —
or allow the access later under **System Settings → Privacy & Security → Files & Folders**. Then
restart the host.

If none of these find adb, check in a new terminal that `command -v adb` prints its path, or set
`ANDROID_HOME`, then restart the host.

On a machine with no `adb` at all — a desktop-, iOS-, or web-only setup — there is nothing to wire,
so the host reports it once in its logs and leaves auto port mapping inactive. Nothing else about
the host is affected, which is why the setting ships enabled.

## Manual setup (if you prefer)

If you'd rather manage the forwarding yourself, turn the setting off and run:

```shell
adb reverse tcp:<serverPort> tcp:<serverPort>
```

after each device connection, using the port shown in
[**Settings → Connection → Debug Server**](/guide/host-settings#debug-server) (repeat for the wss
port if the agent connects over wss).

::: tip Non-Android platforms
Desktop, iOS Simulator, and Web debuggees run on the same machine (or share its network stack), so
they reach the host directly on `localhost` — no port mapping is involved.
:::
