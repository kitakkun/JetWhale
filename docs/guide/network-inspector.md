# Network Inspector

The Network Inspector is a built-in JetWhale plugin for inspecting the HTTP traffic of your app —
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
    connection { host = "localhost"; port = 5080 }
    plugins { register(networkAgent) }
}
```

### OkHttp

```kotlin
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.okhttp.okHttpInterceptor

val networkAgent = JetWhaleNetworkAgentPlugin()

val client = OkHttpClient.Builder()
    .addInterceptor(networkAgent.okHttpInterceptor()) // application interceptor
    .build()

startJetWhale {
    connection { host = "localhost"; port = 5080 }
    plugins { register(networkAgent) }
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
and response — headers, bodies (with a dedicated JSON view), and status. Use **copy** on a
transaction to share it or reproduce the request elsewhere.

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
test backend. Toggle mocking on/off at any time from the host — no app restart needed.
