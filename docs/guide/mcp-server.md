# MCP Server <Badge type="warning" text="experimental" />

The JetWhale host embeds an **MCP (Model Context Protocol) server**, so AI agents such as Claude
can inspect and drive your debugging sessions. The built-in tools operate on a **plugin's UI inside
the host** — capture it, click, type, scroll, and read its semantics tree — so an agent can use any
plugin the way you do (and, through a plugin's own capabilities, reach the debuggee app itself).

## Connecting an AI agent

The server speaks MCP over **SSE**, listening on port **7080** by default (configurable in
**Settings**):

- `GET http://localhost:7080/sse` — the SSE stream
- `POST http://localhost:7080/message?sessionId=...` — client-to-server messages

For example, to register it with Claude Code:

```shell
claude mcp add --transport sse jetwhale http://localhost:7080/sse
```

## Built-in tools

| Tool | What it does |
|------|--------------|
| `jetwhale.listSessions` | Lists connected debug sessions; other tools take a `sessionId` from here |
| `jetwhale.listPlugins` | Lists the plugins available in a session |
| `jetwhale.screenshot` | Captures the current rendered frame of a plugin's Compose UI as a PNG |
| `jetwhale.click` | Dispatches a mouse click at pixel coordinates in a plugin's UI |
| `jetwhale.type` | Types text or a special key into a plugin's UI |
| `jetwhale.scroll` | Dispatches a scroll event in a plugin's UI |
| `jetwhale.drag` | Simulates a drag gesture in a plugin's UI |
| `jetwhale.getAccessibilityTree` | Returns the Compose semantics (accessibility) tree of a plugin's UI |

Because a single host can debug multiple apps at once — each with several plugins — every tool that
targets a plugin UI takes required `sessionId` **and** `pluginId` parameters: call
`jetwhale.listSessions` first, then `jetwhale.listPlugins` to pick the plugin.

## Plugin-provided tools

Host plugins can expose their own MCP tools by implementing `JetWhaleMcpCapablePlugin`. Their tools
are registered alongside the built-ins; JetWhale automatically injects a required `sessionId`
parameter into each plugin tool's schema so an AI agent can target a specific connected device with
your custom debugging features too. See
[Developing Plugins → Exposing MCP tools](/guide/developing-plugins#exposing-mcp-tools) for how to
write one.

The [Network Inspector](/guide/network-inspector#mcp-tools) ships a full set of plugin tools
(`com.kitakkun.jetwhale.network.*`) for reading captured traffic and managing mock rules.

::: tip Sensitive values
Plugin UIs can hide sensitive content from `jetwhale.screenshot` captures via the
`LocalIsScreenshotCapture` CompositionLocal, and the Network Inspector's
[redaction rules](/guide/network-inspector#redacting-sensitive-values) support an `MCP_ONLY` scope
that keeps values visible to you but hidden from AI agents.
:::

::: warning
The MCP server is experimental — tool names and behavior may change between releases.
:::
