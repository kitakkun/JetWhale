# ADB Auto Port Mapping

When you debug an **Android device or emulator**, the debuggee app connects to the JetWhale host's
WebSocket server on `localhost`. For that to work, the device needs an
[`adb reverse`](https://developer.android.com/tools/adb) port forwarding so that `localhost:<port>`
on the device reaches the host machine.

JetWhale can manage this for you: with **ADB auto port mapping** enabled, the host watches ADB for
device connections and sets up (and tears down) the reverse port forwarding automatically — no
manual `adb reverse` commands needed.

## Enabling it

Open **Settings → General** in the JetWhale host and toggle **ADB auto port mapping**.

While enabled, the host:

1. Locates your `adb` binary automatically (see [How adb is found](#how-adb-is-found)).
2. Runs `adb track-devices` to watch devices connect and disconnect.
3. When a device comes online, runs the equivalent of:

   ```shell
   adb -s <serial> reverse tcp:<serverPort> tcp:<serverPort>
   ```

   where `<serverPort>` is the host's WebSocket server port.
4. When a device goes offline, removes its reverse mapping.

If the ADB server restarts or crashes (for example, another tool ran `adb kill-server`), JetWhale
automatically re-attaches to the device tracking stream after a short backoff — you don't need to
toggle the setting again.

Disabling the setting stops the device tracking and removes all reverse mappings JetWhale created.

## How adb is found

JetWhale looks for the `adb` executable in the usual locations, in order:

- Common binary directories such as `/usr/bin` and `/usr/local/bin`
- The default Android SDK location — `$HOME/Android/Sdk/platform-tools` on Linux,
  `$HOME/Library/Android/sdk/platform-tools` on macOS
- `$ANDROID_HOME/platform-tools` and `$ANDROID_SDK_ROOT/platform-tools`
- Finally, plain `adb` resolved via your `PATH`

If none of these work in your environment, make sure `adb` is on the `PATH` of the shell that
launches the JetWhale host, or set `ANDROID_HOME`.

## Manual setup (if you prefer)

If you'd rather manage the forwarding yourself, leave the setting off and run:

```shell
adb reverse tcp:<serverPort> tcp:<serverPort>
```

after each device connection, using the server port shown in **Settings → General**.

::: tip Non-Android platforms
Desktop, iOS Simulator, and Web debuggees run on the same machine (or share its network stack), so
they reach the host directly on `localhost` — no port mapping is involved.
:::
