# MCP Server <Badge type="warning" text="experimental" />

The JetWhale host embeds an **MCP (Model Context Protocol) server**, so AI agents such as Claude
can inspect and drive your debuggee apps directly — take screenshots, click, type, scroll, and read
the accessibility tree.

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
| `jetwhale.screenshot` | Captures the debuggee's screen |
| `jetwhale.click` | Clicks/taps at a position or element |
| `jetwhale.type` | Types text |
| `jetwhale.scroll` | Scrolls |
| `jetwhale.drag` | Performs a drag gesture |
| `jetwhale.getAccessibilityTree` | Returns the accessibility (semantics) tree of the current UI |

Because a single host can debug multiple apps at once, every tool that targets a device takes a
required `sessionId` parameter — call `jetwhale.listSessions` first to find the session you want.

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
