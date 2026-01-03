# JetWhale Protocol

This module defines a communication protocol used between the JetWhale debugger and the debuggee
application.

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
2. **Session ID Exchange**: The debugger assigns a unique session ID to distinguish each debuggee.
   The debuggee needs to get assigned a session ID from the debugger. If the debuggee wants to
   resume a previous session, it can provide the existing session ID.
3. **Capabilities Exchange**: Both the debugger and debuggee exchange their capabilities to
   understand what features are supported during the debugging session.
4. **Plugin Compatibility Check**: The debuggee sends a list of registered plugins to the debugger.
   The debugger checks the compatibility of these plugins and toggle their availability accordingly.
5. **Debugging Session Start**: Once all the above negotiations are complete, the debugging session
   is ready to start.

## Debugging Session

During the debugging session, both sides can send or receive the following types of messages:

- **Debuggee Event**: This message is sent from the debuggee to the debugger. The debuggee can
  send various types of information to support plugin debugging features. This is a unidirectional
  message, so the debuggee does not expect any response from the debugger.
- **Method**: This message is sent from the debugger to the debuggee to request specific actions or
  information. The debuggee processes the method and sends back a corresponding response message.
