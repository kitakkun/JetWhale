# JetWhale Protocol

This page describes the communication protocol used between the JetWhale debugger (host) and the
debuggee application. The protocol is defined in the
[`jetwhale-protocol`](https://github.com/kitakkun/JetWhale/tree/main/jetwhale-protocol) module.

## Two Phases Before Starting a Debugging Session

There are two main phases in the JetWhale Debugger Protocol:

1. **Negotiation**: The debugger and debuggee exchange initial information to establish a
   connection (e.g., protocol version, session ID).
2. **Debugging Session**: The debugger sends methods to the debuggee, and the debuggee
   responds with the requested information or actions. The debuggee can also send events to the
   debugger at any time during the session.

## Negotiation

The negotiation phase consists of the following steps:

1. **Protocol Version Exchange**: The debuggee sends its supported protocol version to the
   debugger. The debugger accepts or rejects the version based on its own supported versions.
2. **Session ID Exchange**: The debugger assigns a unique session ID to distinguish each debuggee.
   The debuggee needs to get assigned a session ID from the debugger. If the debuggee wants to
   resume a previous session, it can provide the existing session ID.
3. **Plugin Compatibility Check**: The debuggee sends a list of registered plugins to the debugger.
   The debugger checks the compatibility of these plugins and toggles their availability accordingly.
4. **Debugging Session Start**: Once all the above negotiations are complete, the debugging session
   is ready to start.

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
