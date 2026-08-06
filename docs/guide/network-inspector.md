# Network Inspector

The Network Inspector is an official JetWhale plugin for inspecting the HTTP traffic of your app —
and for **mocking responses** without touching your backend.

- 📡 Live view of HTTP transactions (request/response headers, bodies, timing)
- 🔍 JSON body viewer for structured responses
- 📋 Copy transactions for sharing or reproducing requests — the detail pane is fully
  text-selectable
- 🎭 Response mocking with configurable rules, toggled from the host UI
- 🙈 [Redaction rules](#redacting-sensitive-values) to keep secrets (auth headers, tokens,
  passwords) out of captured traffic

It works with **Ktor** and **OkHttp** clients.

## Setup

### Install the host plugin

The Network Inspector is in the host's **official catalog**: open **Settings → Plugins → Add Plugins
→ Official Plugins** and install it with one click — no coordinates needed. See
[Host Settings → Plugins](/guide/host-settings#plugins) for the other install routes.

### Add the agent to your app

Add the core agent plus the adapter for your HTTP client to the app being debugged:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    // pick the adapter(s) matching your HTTP client:
    implementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-ktor:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-okhttp:<version>")
}
```

Create **one** `JetWhaleNetworkAgentPlugin` instance and use it in two places: install it into your
HTTP client, and register it in `startJetWhale { }`. It must be the same instance.

### Ktor

```kotlin
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorClientPlugin

val networkAgent = JetWhaleNetworkAgentPlugin()

val client = HttpClient {
    install(networkAgent.ktorClientPlugin())
}

startJetWhale {
    connection {
        endpoints {
            ws("localhost", 5080)
        }
    }
    plugins {
        register(networkAgent)
    }
}
```

#### Attaching to a client you didn't build

When the `HttpClient` comes from a DI container or a library, use the `HttpSend` interceptor
instead — it attaches to an already-built client, so the construction site stays untouched:

```kotlin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorSendInterceptor
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin

val networkAgent = JetWhaleNetworkAgentPlugin()
val client = HttpClient()

client.plugin(HttpSend).intercept(networkAgent.ktorSendInterceptor(client))
```

Pass the same client the interceptor is registered on — it is used to synthesize mocked responses.
Register it once per client at setup: `HttpSend` neither rejects duplicates nor offers a way to
remove an interceptor, so registering twice records every transaction twice.

### OkHttp

```kotlin
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.okhttp.okHttpInterceptor

val networkAgent = JetWhaleNetworkAgentPlugin()

val client = OkHttpClient.Builder()
    .addInterceptor(networkAgent.okHttpInterceptor()) // application interceptor
    .build()

startJetWhale {
    connection {
        endpoints {
            ws("localhost", 5080)
        }
    }
    plugins {
        register(networkAgent)
    }
}
```

Add the interceptor **after** interceptors that finalize the request (e.g. auth interceptors), so
the recorded transaction matches what actually goes on the wire.

### Body capture limit

Both adapters capture request/response bodies up to a limit (default `100_000` characters), and
accept it as a parameter:

```kotlin
networkAgent.ktorClientPlugin(maxBodyChars = 500_000)
networkAgent.okHttpInterceptor(maxBodyChars = 500_000)
```

## Inspecting traffic

Open the **Network Inspector** plugin in the JetWhale host and select your app's session. Each HTTP
transaction appears live as your app makes requests. Select a transaction to inspect its request
and response — headers, bodies (with a dedicated JSON view), and status. Right-click a transaction
for **Copy as cURL**, **Copy URL** and its request/response bodies, to share it or reproduce the
request elsewhere.

The host keeps the **latest 500 transactions** per session; older ones are dropped as new traffic
arrives. Use **clear** (or `com.kitakkun.jetwhale.network.clearTransactions`) before reproducing an
issue so what you capture afterwards is only what the reproduction produced.

Traffic captured while the host is away is **not** lost: the agent buffers up to **256** events
(dropping the oldest past that) and flushes them on reconnect, so requests fired before you opened
the host still show up.

## Redacting sensitive values

Captured traffic often contains secrets — `Authorization` headers, session cookies, tokens in query
parameters, passwords in JSON bodies. Pass **redaction rules** to the agent plugin to strip them:

```kotlin
val networkAgent = JetWhaleNetworkAgentPlugin(
    redaction = NetworkRedactionRules {
        header("Authorization", "Cookie")
        header("X-Session-Id", scope = RedactionScope.MCP_ONLY)
        urlQueryParam("token", strategy = RedactionStrategy.MASK)
        bodyJsonField("password", "access_token")
    },
)
```

Three rule targets are available — `header(...)`, `urlQueryParam(...)`, and `bodyJsonField(...)`
(matches the field name anywhere in a JSON body). Name matching is case-insensitive, and each rule
takes two options:

- **`scope`** — where the rule is enforced:
  - `EVERYWHERE` (default): applied at capture time on the agent, so the value never leaves the
    debuggee process.
  - `MCP_ONLY`: the value stays visible in the host UI, but is hidden from AI agents connected via
    the [MCP server](/guide/mcp-server).
- **`strategy`** — how the redacted value is rendered: `PLACEHOLDER` (default, a `<redacted>`
  marker) or `MASK` (one `*` per character, preserving the value's length).

Without a `redaction` argument no rules apply and captured data is forwarded verbatim.

## Mocking responses

The Mocks view lets you define **mock rules** on the host and push them to the running app: when
mocking is enabled, requests matching a rule get the mocked response instead of hitting the
network. This is handy for reproducing error states, empty lists, or slow-path payloads without a
test backend. Toggle **Mocking enabled** on/off at any time from the host — no app restart needed.

### What a rule looks like

**Add rule** opens an editor with these fields. The same shape is what the
[MCP tools](#mcp-tools) take, so a rule written by hand and one written by an AI agent are
interchangeable.

| Field | Default | Meaning |
|-------|---------|---------|
| **Name** | empty | Human-readable label, shown in the list. |
| **enabled** | on | Whether the rule takes effect. Rules can be parked without deleting them. |
| **Method** | any | HTTP method to match, compared case-insensitively. Blank matches any method. |
| **URL pattern** | — | The pattern, interpreted per the match type. |
| **match type** | `CONTAINS` | `CONTAINS` (substring), `EXACT` (whole URL), or `REGEX` (a regex that must match somewhere in the URL). An invalid regex never matches. |
| **Status** | `200` | Status code of the mocked response. |
| **Content-Type** | none | Convenience field for the header of the same name. |
| **Response body** | empty | The body to return. |
| **Delay ms** | `0` | Artificial delay before the mocked response is delivered. |

The **first enabled rule that matches** wins, so order the list from most specific to most general.
Matching is identical in every adapter, because they share one implementation.

::: tip The app owns the mock config
The mock rules and the enabled flag live on the **agent**, not the host — they survive a host
restart, and the host fetches them back when it reconnects. That is also why a rule you add applies
immediately without restarting the app.
:::

## MCP tools

The Network Inspector contributes its own tools to the host's [MCP server](/guide/mcp-server), so
an AI agent can read captured traffic and manage mock rules for a session:

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.network.listTransactions` | Lists captured HTTP transactions, filterable |
| `com.kitakkun.jetwhale.network.getTransaction` | Returns one transaction in full (headers, bodies, timing) — takes `txId` from `listTransactions` |
| `com.kitakkun.jetwhale.network.clearTransactions` | Clears the captured transaction list |
| `com.kitakkun.jetwhale.network.getMockConfig` | Returns the current mock rules and whether mocking is enabled |
| `com.kitakkun.jetwhale.network.addMockRule` | Adds one mock rule from flat arguments |
| `com.kitakkun.jetwhale.network.removeMockRule` | Removes the rule with a given `id` |
| `com.kitakkun.jetwhale.network.setMockRules` | Replaces the **whole** rule list in one call |
| `com.kitakkun.jetwhale.network.setMockingEnabled` | Turns mocking on or off |

Like every MCP tool, they take a required `sessionId` (from `jetwhale.listSessions`).

`listTransactions` narrows a busy capture rather than dumping it: `limit`, `afterTxId` (everything
recorded after a transaction you already have — the cheap way to poll), `sinceTimestampMs` /
`untilTimestampMs`, `urlContains` and `method`.

`addMockRule` takes the rule's fields flat (`urlPattern`, `matchType`, `method`, `name`,
`statusCode`, `body`, `headers`, `contentType`, `delayMs`), appends one enabled rule with a generated
id, and returns it. `contentType` is a convenience that only fills in a `Content-Type` header when
`headers` did not already set one.

`setMockRules` instead takes a JSON list of complete rules and **replaces** the whole set — the tool
to reach for when setting up a scenario, editing a rule (reuse its `id`), or disabling one
(`enabled: false`) rather than deleting it.

[Redaction rules](#redacting-sensitive-values) apply to MCP output as well: values redacted with
`RedactionScope.MCP_ONLY` are hidden from these tools' results **and** from `jetwhale.screenshot`
and `jetwhale.getAccessibilityTree` captures of the Network Inspector UI, while staying visible to
you in the host window.
