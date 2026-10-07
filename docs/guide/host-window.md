# The Host Window

The JetWhale host is one window: a **sidebar** down the left side that picks what you are debugging
and which tool you are looking at, and the selected plugin's own UI filling the rest. From top to
bottom, the sidebar holds the [AI activity](#ai-activity) header, the
[plugins that need no app](#plugins-that-need-no-app), the [app picker](#choosing-an-app), the
selected app's [plugin list](#the-plugin-list), and the [footer](#the-sidebar-footer).

The plugins are documented on their own pages; this page covers the host around them.

## AI activity

The sidebar header shows whether an AI agent is connected over [MCP](/guide/mcp-server); with none
connected it holds only the collapse control. While one is connected it reads *AI agent connected*.
While a call runs, a rotating ring goes round the header and the tool's short name (`mirror.tap`)
takes the text's place, with the full name on hover. Clicking it opens the details — the tool, the
plugin it operates, the app — and the **Follow the AI** switch, the same setting as
[AI Activity](/guide/host-settings#ai-activity).

## Plugins that need no app

Plugins that need no app (`"requiresAgent": false` — the Device Mirror, for one) are listed at the
top of the sidebar, above the app picker. They work as soon as the host starts, with nothing
connected, and run once rather than once per app. Switching apps never moves one that is on screen,
and a debug-server restart does not close it. Over MCP they live in a session of their own, `host`,
which `jetwhale.listSessions` always lists first.

## Choosing an app

One host commonly holds several apps from the same device, so sessions are picked with two
dropdowns:

1. **Select Device** — one entry per device, keyed by the `deviceId` the agent reported.
2. **Select App** — the apps connected from that device. It appears once the device has more than
   one app; picking a device selects its first app, so a session is always active.

When nothing is connected the device row reads *No app connected*. When a session goes away the host
says so (*&lt;device&gt; · &lt;app&gt; disconnected*, or *N sessions disconnected* when several drop
at once, as after a debug-server restart).

### Session security indicator

Each entry carries a lock icon for how its connection is secured:

| Lock | Meaning |
|---|---|
| Green | Connected over TLS (**wss**); encrypted end to end. |
| Neutral | Plain ws over a loopback peer — an app on this machine or an ADB-forwarded device. The traffic never leaves the machine. |
| None | Plain ws to a non-loopback peer; the traffic is unencrypted on the network. |

## The plugin list

Under the picker are the selected app's plugins, enabled ones first. The rest follow in two grayed
groups, each under a fold row:

- **N disabled** — installed but switched off; open to begin with unless there are more than two.
  Clicking one opens a screen with an **Enable** button.
- **N not in this app** — installed in the host, but the app's agent never advertised the plugin;
  folded to begin with. Clicking one shows how to add it to the app: the Gradle dependency of its
  agent and its `register(...)` call to copy, with a link to an official plugin's guide.

With no app connected, the list says *Connect an app to see its plugins.* With no plugins installed
at all, the sidebar says so, with a shortcut to the plugin settings when some jars failed to load;
see [Host Settings → Plugins](/guide/host-settings#plugins).

Every row except a "not in this app" one has an overflow (**⋯**) menu:

- **Disable** / **Enable** — host-wide, the same toggle as `jetwhale.setPluginEnabled` over MCP.
- **Pop out** — moves the plugin into a window of its own, so you can watch two plugins, or one plugin
  on two sessions, side by side. The main window offers **Bring back to main window**, and the menu
  entry becomes **Bring back**. A plugin with no UI cannot pop out.

### MCP badges

A plugin that contributes [MCP tools](/guide/mcp-server#plugin-provided-tools) carries an **MCP**
badge on its row. Clicking the badge opens the
[MCP tools browser](/guide/mcp-server#the-mcp-tools-browser) filtered to that plugin and the selected
session. While an agent calls one of the plugin's tools, the badge fills with the accent color and
the row takes a rotating ring, so the plugin being driven is visible even when its label has
scrolled away.

## The sidebar footer

| Entry | What it opens |
|-------|---------------|
| **Browse MCP tools** (wrench icon) | The [MCP tools browser](/guide/mcp-server#the-mcp-tools-browser), unfiltered. |
| **Settings** (gear icon) | [Host Settings](/guide/host-settings). |
| **About JetWhale** (info icon) | The version, project links, and **OSS Licenses**: the open-source components the host ships. |

Banners above the plugin area report on updates, which are never applied without a click; see
[Host Settings → Application](/guide/host-settings#application).

## Collapsing the sidebar

The collapse button in the sidebar header shrinks it to an icon rail, and the same button expands it
again. The rail lists only the **enabled** plugins, with no groups and no overflow menu, and its
session picker is one flat list of *device · app*. Every icon names itself in a tooltip.

## The log viewer

**Settings → General → Application → View Application Logs** opens the host's own captured
stdout/stderr in a separate window: filter by substring, toggle auto-scroll, and clear the buffer.
This is the **host's** log, not the debugged app's: it is where a plugin jar that failed to load, or
a server that failed to bind, reports itself. The same buffer backs the `jetwhale.getLogs` and
`jetwhale.clearLogs` [MCP tools](/reference/mcp-tools#host-tools).
