# Connecting Devices

An app reaches the host over a WebSocket, and where the app runs decides the route. Anything that
shares the host machine's loopback connects to `localhost` in the clear; a physical device on the
network connects to the machine's LAN address over **wss**. This page covers each route, how the
app finds the host, and which certificate it trusts.

## Which route your app takes

| App runs on | Reaches the host through | What to set up |
|---|---|---|
| Desktop (JVM), iOS Simulator, a browser | `localhost` | Nothing |
| Android emulator, or a device on USB | `localhost`, forwarded by `adb reverse` | Nothing: [ADB auto port mapping](/guide/adb-auto-port-mapping) wires it |
| iPhone or Android device on Wi-Fi | The host machine's LAN address, over wss | [Find the host](#finding-the-host-on-the-network) or [bake in its address](#baking-in-the-build-machine-s-address), [trust its certificate](#secure-connections-wss), and on iOS [allow local network access](#ios-local-network-permission) |

The host binds plain ws to loopback only and wss to every interface; see
[Host Settings → LAN exposure](/guide/host-settings#lan-exposure).

## Endpoints

`endpoints { }` lists the candidates the agent tries, **in the order they are declared**. Each one
names its own scheme, so one configuration can serve targets that cannot use the same one:

| Candidate | Contributes |
|---|---|
| `ws(host, port)` | That address, in the clear |
| `wss(host, port)` | That address, over TLS |
| `discoverWss { }` | Every host on the network that answers and passes the block's policy, over wss |
| `buildMachineWss(port)` | The build machine's address, baked in at compile time, over wss |

```kotlin
startJetWhale {
    connection {
        endpoints {
            // Emulators, simulators, ADB-forwarded devices, the desktop app and the browser reach
            // loopback, so they connect here and never wait for a network browse.
            ws("localhost", 5080)

            // A physical device reaches none of those, falls through, and connects over wss.
            discoverWss { allowHostName("my-macbook") }
        }

        ssl { trustServerCertificate() }
    }
    plugins { /* ... */ }
}
```

On a device, `localhost:5080` is refused at once, since nothing listens on the device itself, and
discovery takes over. Everywhere else the first candidate connects and the browse never runs. The
agent works through the whole list in each round and waits only when every candidate refused; see
[Reconnecting](/guide/agent-configuration#reconnecting).

`ws(...)` sends in the clear. Over loopback that is unremarkable: the traffic cannot leave the
machine, and the host serves plain ws there alone. Anywhere else it is plain text on the network,
so the agent logs a line saying so at startup.

The deprecated `host` and `port` properties still work, as a single candidate. They are the one case
where `ssl { }` decides the scheme, since they name none of their own.

## Finding the host on the network

A physical device on the same Wi-Fi cannot reach the host over `localhost`. Rather than hardcoding
the machine's LAN IP, which changes between machines and networks, declare `discoverWss { }` and let
the agent find the host over mDNS (Bonjour).

While its debug server runs, the host advertises a `_jetwhale._tcp.local.` service whose TXT records
carry the `wsPort`, the `wssPort` (only when wss is enabled), the machine's `hostName` and a protocol
marker `v=1`, so discovery resolves the port as well as the address. A host advertising no wss port
is passed over, and the log names it. There is no plain-ws counterpart: the host binds ws to
loopback, so a discovered address would refuse it anyway.

::: warning `discoverWss { }` needs a policy
A block that states nothing accepts nothing, and logs why. Discovery reaches **every** JetWhale host
on the network, so on a shared one an unqualified browse can hand your app's debug traffic to a
colleague's host. Name your machine:

```kotlin
discoverWss { allowHostName("my-macbook") }  // or allowAddress("192.168.3.26")
```

`allowAll()` takes any host advertising the service. It is right on a network that is exclusively
yours, and something to ask for rather than receive by omission.
:::

- **`allowHostName(name)`** matches the advertised host name — the `hostName` TXT record, falling
  back to the mDNS instance name — exactly and case-insensitively.
- **`allowAddress(ip)`** matches a resolved IP, which is what every platform connects by.

Both are repeatable allowlists: a second call widens the list rather than replacing it. An empty list
means no restriction on that, so a host must match every list that has entries.

These filters choose a host; they do not authenticate it. mDNS is unauthenticated, so anyone on the
network can advertise any `hostName`. Authentication is the certificate's job; see
[Which certificate to trust](#which-certificate-to-trust).

What is declared after `discoverWss` is still reached when discovery found hosts, because answering
mDNS does not mean accepting a connection. Each round browses afresh, so the app can start before the
host, and a host restarted on another port is found again. A failed round is reported once, not on
every retry.

| Platform | Discovery backend |
|----------|-------------------|
| **JVM (Desktop)** | jmDNS |
| **Android** | `NsdManager` (`android.net.nsd`) |
| **iOS / macOS** | `NSNetServiceBrowser` (Foundation / Bonjour) |
| **JS / Wasm / Linux / Windows** | Not supported: contributes nothing, and the next candidate is reached at once |

On iOS the browse also needs `NSBonjourServices` in `Info.plist`; see
[iOS Local Network permission](#ios-local-network-permission).

## Baking in the build machine's address

When the host runs on the machine that compiles the app, the usual arrangement, its address is
already known at build time, and a browse is a slow way to find it again. `buildMachineWss(port)`
bakes it in. Apply the agent Gradle plugin to the module that declares the endpoint:

```kotlin
// the app being debugged — build.gradle.kts
plugins {
    id("com.kitakkun.jetwhale.agent") version "<version>"
}
```

```kotlin
endpoints {
    ws("localhost", 5080)   // emulators, simulators, the desktop app, the browser
    buildMachineWss(5443)   // physical devices — no browse, no Info.plist entry
}
```

At compile time the call becomes `wss("192.168.3.26", 5443)`, with whatever the machine's address
was, and the build log says so once per module:

```
w: JetWhale: baked the build machine address 192.168.3.26 into 1 buildMachineWss call(s) in 'shared'.
```

::: warning `@ExperimentalJetWhaleApi`
This candidate comes from a Kotlin compiler plugin, and JetBrains changes the compiler plugin API
across minor versions by design. CI proves the shipped plugin against **Kotlin 2.3.0 – 2.4.x**, and
the Gradle plugin checks your Kotlin version up front:

- **Below 2.3** the build **fails** with an explanation: `CompilerPluginRegistrar.pluginId` is
  abstract from 2.3 and absent before it, so the plugin cannot load. Write the address out with
  `wss()` instead.
- **Above the highest tested minor** the build warns and carries on. If the plugin then fails to
  load, drop it and use `wss()` until JetWhale catches up.
:::

### Without the Gradle plugin

The call still compiles. It contributes no candidate and logs why:

```
buildMachineWss(5443) was declared but the agent Gradle plugin ('com.kitakkun.jetwhale.agent')
is not applied to this module, so no build machine address was baked in and this contributes no
candidate. Apply that plugin, or write the address out with wss().
```

A missing build-time convenience should not be a broken build, so this is deliberate. Keep a
`discoverWss { }` or a written-out `wss(...)` after it if the connection has to work either way.

### Choosing the address

Detection asks the routing table which source address reaches the wider network. Override it where
that is not the answer — several interfaces with the device on the other one, a VPN holding the
default route, a host reached through a forwarded port:

```kotlin
jetwhaleAgent {
    address = "192.168.3.26"
}
```

With neither an override nor a detected address, as on an offline machine, nothing is baked in and
the runtime explains why rather than the build failing.

### Build cache

The address is a compile task input, deliberately:

| | |
|---|---|
| A stale address surviving an "up-to-date" build | Cannot happen: changing it reruns the compilation. |
| Moving between networks | Recompiles the modules that apply the plugin, so apply it only to the module that declares the endpoint. |
| Remote or shared build cache | Those compilations are not shared, since the address is per developer. |

## Secure connections (wss)

The host serves plain **ws** on port **5080** and, unless you turn it off, **wss** on port **5443**,
backed by a locally issued CA. The CA is generated the first time the wss connector starts, so there
is nothing to set up; [Host Settings → SSL certificates](/guide/host-settings#ssl-certificates) is
where to export, replace or switch it.

Whether TLS is spoken is the candidate's business — `wss(host, port)` instead of `ws(host, port)` —
and `ssl { }` only says which certificates to trust once it is. A `wss(...)` candidate with no
`ssl { }` uses the platform's own trust store, as an ordinary HTTPS request would.

```kotlin
startJetWhale {
    connection {
        endpoints {
            wss("localhost", 5443)
        }

        ssl {
            // Option A: fetch and pin the host's active CA automatically (trust on first use).
            trustServerCertificate()

            // Option B: pin a CA you exported from the host's SSL Certificate settings.
            // trustCertificate(pem = "-----BEGIN CERTIFICATE-----\n...")
        }
    }
    plugins { /* ... */ }
}
```

A browser cannot use wss with JetWhale. TLS trust belongs to the browser, so
`trustServerCertificate()` has nothing to pin with, and the CA endpoint is a different origin with
no CORS headers. `ws("localhost", ...)` has neither problem.

### Which certificate to trust

- **`trustServerCertificate()`** downloads the host's active CA from `/jetwhale/ca` when it
  connects and pins the wss connection to it, so no PEM is hardcoded in the app. It tries
  `http://<host>:<port>/jetwhale/ca` first, the plain channel used over localhost and ADB
  forwarding, then `https://<host>:<port>/jetwhale/ca` with verification disabled, for a LAN device
  on the wss port while the plain server is bound to loopback. Both are trust on first use: the
  fetch itself is not authenticated, though the fetched CA still pins the session. Over ADB
  forwarding the download never leaves the machine. If the CA cannot be fetched, the candidate is
  still dialed over wss against the platform's trust store, so it fails visibly on trust rather than
  sending in the clear.
- **`trustCertificate(pem)`** pins a CA you exported yourself from the host's
  [SSL Certificate](/guide/host-settings#ssl-certificates) settings (**Show Details → Copy to
  Clipboard**). Prefer it on a network you do not trust: the handshake completes only with a host
  holding the matching key.

### Per-platform pinning

| Platform | Behavior |
|----------|----------|
| **JVM / Android** | Full pinning via a custom `X509TrustManager` built from the configured PEMs. Invalid PEMs log a warning and fall back to system trust. |
| **iOS / macOS** | Full pinning via Security.framework anchor certificates (`SecTrustSetAnchorCertificates`), so the local CA is trusted without installing it in the device trust store. A physical iPhone fetches the CA over the wss port and needs the [Local Network permission](#ios-local-network-permission). |
| **Linux** | Pinning via curl's `CURLOPT_CAINFO`: the PEMs are written to a private per-process CA bundle file under the temp dir and pinned against it. |
| **Windows** | WinHttp validates only against the Windows certificate store and cannot pin a custom CA in code. Install the exported CA into the store yourself, e.g. `certutil -user -addstore Root jetwhale-ca.pem`. |
| **Web (JS / WasmJS)** | The browser manages TLS; custom CA configuration is not supported and is ignored with a warning. |

## iOS Local Network permission

A physical iPhone connects to the host over the local network, and both the wss connection and the
CA fetch go over the LAN. iOS gates local-network access behind a user permission, so add a usage
description to the app's `Info.plist`:

```xml
<key>NSLocalNetworkUsageDescription</key>
<string>JetWhale connects to the debugger host running on your local network.</string>
<!-- Required when using discoverWss: iOS blocks the Bonjour browse without it. -->
<key>NSBonjourServices</key>
<array>
    <string>_jetwhale._tcp</string>
</array>
```

iOS asks the user on the first connection. `NSBonjourServices` is required whenever you use
[`discoverWss`](#finding-the-host-on-the-network), because iOS silently blocks browsing for a service
type that is not declared; an app that dials the host by address can leave it out. The CA fetch falls
back to `https` over the wss port, so no App Transport Security exception for plain HTTP is needed.
