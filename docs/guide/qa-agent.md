# QA Agent <Badge type="warning" text="experimental" />

Testing a host plugin means having a **connected session** for it to render against — the plugin
screen is empty without one. A demo app is a poor stand-in: it only fires the requests its buttons
are wired to, and a GUI app cannot be driven from a script at all.

The **JetWhale QA agent** is a headless debuggee for exactly this. It connects to the host as an
ordinary debug session and exposes a small HTTP **control API** you drive from a terminal, a test, or
an AI agent — so your plugin's host UI can be exercised end to end without writing an app.

It is **plugin-agnostic**: you name the plugin ids it should impersonate, and `/send` / `/request`
carry arbitrary messages to their host counterparts over the messaging layer's raw lane. There is no
compile-time dependency on the plugin under test, so it works for plugins living outside this
repository.

## Running it

The [`com.kitakkun.jetwhale.host`](/guide/developing-plugins) Gradle plugin adds a
`runJetWhaleQaAgent` task to your plugin module. It resolves
`com.kitakkun.jetwhale:jetwhale-qa-agent` at `jetwhalePlugin.qaAgentVersion`, falling back to
`hostVersion`:

```shell
# Terminal 1 — the host with your plugin loaded
./gradlew :myPlugin:runJetWhaleHot

# Terminal 2 — a headless session for it to render
./gradlew :myPlugin:runJetWhaleQaAgent
```

Pass the agent's own options with `-PjetwhaleQaAgentArgs` (space-separated):

```shell
./gradlew :myPlugin:runJetWhaleQaAgent \
  -PjetwhaleQaAgentArgs="--plugin com.example.myplugin --port 5443"
```

| Option | Default | What it does |
|--------|---------|--------------|
| `--app <name>` | one app named `qa-agent` | Connect as an app of this name, as its own session. **Repeatable** — one process can hold several apps under one device, which is how the host groups them. Names must be unique. |
| `--plugin <id>[@<version>]` | none | Register a raw-messaging plugin under this id so `/send` and `/request` can drive its host counterpart. Registered for **every** app. Repeatable; the version defaults to `1.0.0`. |
| `--host <name>` | `localhost` | The JetWhale host to connect to. |
| `--port <n>` | `5443` | The host's **wss** port. The agent always dials wss — it trusts the host's active CA automatically — so this is never the plain-ws port (`5080` by default). |
| `--control-port <n>` | `7100` | Port for the agent's own control API. |
| `--help`, `-h` | — | Print the usage text and exit. |

::: warning Address the control API as `127.0.0.1`
It binds loopback IPv4 only. `localhost` may resolve to `::1` first and be refused.
:::

## The control API

| Route | Body | What it does |
|-------|------|--------------|
| `GET /health` | — | `{status, ready, apps:{<name>:{connected, ready}}}`. **Poll this before sending anything**: the control API answers long before the debug session is up, and a send in that window is dropped — `/send` reports it as `sent: false` with a `hint`, but nothing retries it for you. |
| `GET /plugins` | — | Per registered plugin id: `{version, activated, ready, apps:{…}}`. `activated: false` means the host has that plugin **disabled**, so waiting will not help. |
| `POST /send` | `{app?, pluginId, messageType, payload, policy?}` | Sends a fire-and-forget event. `policy` is `DROP` (default), `QUEUE` or `FAIL`. Answers `{sent, hint?}` — `hint` says *why* a `false` happened. |
| `POST /request` | `{app?, pluginId, messageType, payload, timeoutMs?}` | Sends a request and waits for the host's reply: `{durationMs, reply}`. |
| `POST /fire` | `{app?, url, method?, headers?, body?, contentType?}` | Issues a real HTTP request through an instrumented client, so the [Network Inspector](/guide/network-inspector) captures it. Answers `{status, durationMs, bodyPreview}`. |
| `POST /disconnect` | `{app?}` | Gives one app's session up while the process keeps running — this is how the host's disconnect handling gets exercised. Terminal for that app. |
| `POST /shutdown` | — | Stops the process. |

`app` is optional while only one app is running; with several, a call that does not name one is
rejected rather than guessed at. `messageType` is the **serial name** of the message class — the
value `@SerialName` sets, or the fully-qualified class name by default.

```shell
curl -s 127.0.0.1:7100/health

curl -s 127.0.0.1:7100/send -H 'Content-Type: application/json' -d '{
  "pluginId": "com.example.myplugin",
  "messageType": "com.example.myplugin.protocol.ItemAdded",
  "payload": {"id": 1, "label": "hello"}
}'
```

A host handler that fails, times out or was never registered comes back as
`{"error": "..."}` with HTTP 200 — that is a legitimate QA finding about your plugin, not a
control-API error, so it is reported as data.

`/fire` is the one plugin-specific convenience: it needs no `--plugin`, because the Network
Inspector's agent is always registered.

::: warning Sending only
The impersonated plugin exposes the messenger's raw lane, which is a send-side surface: inbound
handlers resolve their serializer from a reified type parameter, so there is no catch-all shape to
register. A host plugin that **requests the agent** — for example one that fetches state in
`onPrepare()` — cannot be answered from the QA agent, and will see that request time out.
:::

## Driving the plugin's UI too

The QA agent gives your plugin a session; the host's [MCP server](/guide/mcp-server) gives you its
UI. Together they close the loop: `POST /send` to put your plugin into a state, then
`jetwhale.screenshot` / `jetwhale.getAccessibilityTree` to check what it rendered and
`jetwhale.click` / `jetwhale.type` to drive it.

For automated runs where nobody is there to tick a permission checkbox, start the host with
[`--mcp-allow-all-permissions`](/guide/mcp-server#lifting-every-permission-for-one-launch).

## Inside this repository

In-repo plugin modules get `runJetWhaleQaAgentLocal` as well, added by the internal
`jetwhale-host-launch` convention. It runs the QA agent straight from the local `:tools:qa-agent`
project instead of resolving a published artifact, and reads the same `-PjetwhaleQaAgentArgs`.
