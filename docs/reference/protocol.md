# JetWhale Protocol

This page describes the communication protocol used between the JetWhale debugger (host) and the
debuggee application. The protocol is defined in the
[`jetwhale-protocol`](https://github.com/kitakkun/JetWhale/tree/main/jetwhale-protocol) module,
published as `com.kitakkun.jetwhale:jetwhale-protocol-core`.

Messages travel as JSON over one WebSocket connection (plain **ws**, or **wss** when the agent
configures a trusted certificate — see
[Secure connections](/guide/getting-started#secure-connections-wss)). Every message carries a stable
`type` discriminator; the current **protocol version is 2**.

## Two Phases Before Starting a Debugging Session

There are two main phases in the JetWhale Debugger Protocol:

1. **Negotiation**: The debugger and debuggee exchange initial information to establish a
   connection (e.g., protocol version, session ID, capabilities).
2. **Debugging Session**: The debugger sends methods to the debuggee, and the debuggee
   responds with the requested information or actions. The debuggee can also send events to the
   debugger at any time during the session.

## Negotiation

The negotiation phase consists of the following steps:

1. **Protocol Version Exchange**: The debuggee sends its supported protocol version to the
   debugger. The debugger accepts or rejects the version based on its own supported versions.
2. **Session Exchange**: The debugger assigns a unique session ID to distinguish each debuggee.
   The debuggee needs to get assigned a session ID from the debugger. If the debuggee wants to
   resume a previous session, it can provide the existing session ID. Alongside the session
   request, the debuggee reports its **app/device metadata** — all fields optional: `appName`,
   `deviceId` (a stable per-device identifier the debugger uses to group sessions), `deviceName`,
   and `appIconPngBase64` (a small app icon, at most 64×64 px; icons whose base64 form exceeds
   32KB are dropped to keep the negotiation payload small). See
   [Session metadata](/guide/getting-started#session-metadata) for how the agent resolves and
   overrides these values.
3. **Capabilities Exchange**: Both the debugger and debuggee exchange their capabilities to
   understand what features are supported during the debugging session.
4. **Plugin Compatibility Check**: The debuggee sends a list of registered plugins to the debugger.
   The debugger checks the compatibility of these plugins and toggles their availability accordingly.
5. **Debugging Session Start**: Once all the above negotiations are complete, the debugging session
   is ready to start.

### Message catalog

Each step is one request from the agent and one response from the host. The `type` discriminators are
stable — changing one breaks compatibility with older implementations — so they are worth naming:

| Step | Agent → host | Host → agent |
|------|--------------|--------------|
| Protocol version | `negotiation/agent/protocol_version` | `negotiation/host/protocol_version_response/accept` or `negotiation/host/protocol_version_response/reject` |
| Session | `negotiation/agent/session` | `negotiation/host/accept_session` |
| Capabilities | `negotiation/agent/capabilities` | `negotiation/host/capabilities_response` |
| Plugins | `negotiation/agent/available_plugins` | `negotiation/host/available_plugins_response` |

A **reject** carries a human-readable `reason` and the versions the host does support. The session
response carries the assigned `sessionId`, which the agent may send back in a later
`negotiation/agent/session` to resume. The plugin response splits the agent's list into
`availablePlugins` (paired, and enabled on the host) and `incompatiblePlugins` (the plugin exists on
both sides, but the agent's `pluginVersion` falls outside the host plugin manifest's
[`agentVersionRange`](/guide/developing-plugins#manifest-reference)); a plugin the host does not have
at all is simply absent from both.

Capabilities are exchanged as a plain `Map<String, String>` in both directions. Nothing consumes them
yet — the step exists so future versions can negotiate optional features without another protocol
version bump.

## Debugging Session

During the debugging session, plugin messages travel as **plugin frames**, carried symmetrically in
both directions (there is no directional difference between the debugger and the debuggee). A frame
is one of:

- **Notification**: a fire-and-forget event. The sender expects no response.
- **Request**: expects a reply; the sender assigns a correlation id and applies a timeout.
- **Reply**: completes a request (as a success payload or a failure message), matched to it by the
  correlation id.

Frames are addressed by plugin id and routed to that plugin's messaging peer on the receiving side.
Notifications and requests are dispatched in arrival order; replies bypass the queue so a handler
awaiting a reply is never blocked behind other traffic.

On the wire a frame is one of `frame/notification`, `frame/request`, `frame/reply/success` or
`frame/reply/failure`. A notification and a request both carry a `messageType` — the serial name of
the concrete payload class — plus the payload as a serialized string; a request adds a
`correlationId`, which the reply echoes back as `inReplyTo`. A reply needs no `messageType`, because
the requester already knows the reply type from the request's `JetWhaleRequest<R>` declaration.

Plugin frames are carried inside `event/agent/plugin_frame` and `event/host/plugin_frame`.

### Plugin activation

The host additionally sends two events of its own:

| Message | Meaning |
|---------|---------|
| `event/host/plugin_activated` | The host enabled this plugin id for the session. The agent plugin's `onActivate()` runs, then `onPrepare()` for the current connection. |
| `event/host/plugin_deactivated` | The host disabled it. The agent plugin's `onDeactivate()` runs. |

Both carry only the `pluginId`. A **disconnect is not a deactivation**: the agent plugin stays
activated across a reconnect, so one that buffers events keeps buffering. See
[Developing Plugins](/guide/developing-plugins) for the lifecycle callbacks these drive.
