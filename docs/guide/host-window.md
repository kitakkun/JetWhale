# The Host Window

The JetWhale host is one window: a **sidebar** down the left side that picks *what you are debugging*
and *which tool you are looking at*, and the selected plugin's own UI filling the rest.

Everything below is the host itself — the plugins it shows are documented on their own pages
([Network Inspector](/guide/network-inspector), [Nav3 Navigator](/guide/nav3-navigator),
[Compose Semantics Inspector](/guide/compose-semantics-inspector)).

## Choosing a session

Sessions are picked with **two dropdowns**, because one host commonly holds several apps from the
same device:

1. **Select Device** — one entry per device, keyed by the `deviceId` the agent reported. Sessions
   that share a device are grouped under it.
2. **Select App** — the apps connected from that device. It only appears once the device has more
   than one app (or one is already selected). Picking a device automatically selects its first app,
   so a session is always active.

Each entry carries a small lock icon for how its transport is secured — see
[Session security indicator](/guide/what-is-jetwhale#session-security-indicator). When nothing is
connected the device row reads *No session available*.

When a session goes away the host says so (*&lt;device&gt; · &lt;app&gt; disconnected*, or
*N sessions disconnected* when several drop at once, e.g. after a debug-server restart).

## The plugin list

Below the session selector, the installed plugins are grouped into three collapsible sections. The
grouping is computed against the **selected session**, so it changes as you switch apps:

| Section | What lands there |
|---------|------------------|
| **Enabled Plugins** | Enabled in settings **and** available for this session. |
| **Disabled Plugins** | Available for this session, but switched off. |
| **Unavailable Plugins** | No session is selected, or the session's agent never advertised this plugin id. Host-only plugins (`"requiresAgent": false`) are available for every active session, so once a session is selected they never land here. |

Click a plugin to open it. Enabled and disabled rows also carry an overflow (**⋯**) menu — an
unavailable row has none, since there is nothing to do with it:

- **Disable** / **Enable** — the same toggle as `jetwhale.setPluginEnabled` over
  [MCP](/guide/mcp-server), applied host-wide rather than per session. This is the only entry a
  disabled row offers.
- **Pop out** — moves the plugin into a window of its own. The main window shows *This plugin is
  popped out. Please check the separate window.* with a **Bring back to main window** button, and
  the sidebar entry's menu switches to **Bring back**. Popping out is how you watch two plugins (or
  the same plugin on two sessions) side by side. A plugin that renders no UI has no scene to move,
  so the entry is not offered for one.

If no plugins are installed at all, the sidebar says so and — when some jars failed to load — offers a
shortcut to the plugin settings screen. See
[Host Settings → Plugins](/guide/host-settings#plugins) for installing them.

### MCP badges

A plugin that contributes [MCP tools](/guide/mcp-server#plugin-provided-tools) carries an **MCP**
badge on its sidebar row. Clicking the badge opens the
[MCP tools browser](/guide/mcp-server#the-mcp-tools-browser) already filtered to that plugin and to
the session currently selected.

While an AI agent is actually calling one of that plugin's tools, the badge fills with the accent
color and the whole row takes an accent-colored rotating ring, so the plugin being driven is
unmistakable even if the label has scrolled out of view. A strip under the session picker reports
the connection itself — *AI agent connected* — and names the tool running underneath it while a call
is in flight.

## The sidebar footer

| Entry | What it opens |
|-------|---------------|
| **Browse MCP tools** (wrench icon) | The [MCP tools browser](/guide/mcp-server#the-mcp-tools-browser), unfiltered — so the tools an agent can reach are visible without first finding a plugin that publishes some. |
| **Settings** (gear icon) | [Host Settings](/guide/host-settings). |
| **About JetWhale** (info icon) | The about panel: version, project links, and **OSS Licenses** — the full list of open-source components the host ships. |

When a newer release is available, a banner appears above the content with a **View in Settings**
shortcut. Updates are never applied automatically — see
[Host Settings → Application](/guide/host-settings#application).

## Collapsing the sidebar

The collapse button in the sidebar header shrinks it to a narrow icon rail; the same button on the
rail expands it again. Collapsed, it keeps the same entries as icons — the session picker, the
plugin list, and the footer's MCP tools, Settings and About buttons — but shortened: the rail lists
only the **enabled** plugins, with no grouping and no overflow menu, and its session picker is a
single flat list of *device · app* rather than two dropdowns. Every icon names itself in a tooltip.

## The log viewer

**Settings → General → Application → View Application Logs** opens the host's own captured
stdout/stderr in a separate window: filter by substring, toggle auto-scroll, and clear the buffer.
This is the **host's** log, not the debugged app's — it is where a plugin jar that failed to load,
or a server that failed to bind, reports itself. The same buffer backs the `jetwhale.getLogs` and
`jetwhale.clearLogs` [MCP tools](/guide/mcp-server#host-tools).
