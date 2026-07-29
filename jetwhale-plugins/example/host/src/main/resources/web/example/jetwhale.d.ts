/**
 * Type declarations for the `window.jetwhale` bridge injected by JetWhale into a web-based host
 * plugin. Copy this file into your web project (and reference it from tsconfig) to get typed access.
 *
 * Payloads are JSON strings on the wire; encode/decode them yourself (e.g. JSON.stringify /
 * JSON.parse) so message shapes stay in sync with your agent's protocol.
 */
interface JetWhaleBridge {
  /** True once the bridge is installed. A `jetwhale:ready` event also fires on `window`. */
  readonly __ready: boolean;

  /** Fire-and-forget event to the agent counterpart. `type` is the message's wire name. */
  send(type: string, payload: string): void;

  /** Request-reply with the agent; resolves with the reply payload, rejects on failure/timeout. */
  request(type: string, payload: string): Promise<string>;

  /** Registers a listener for messages the plugin forwards from the agent. */
  onMessage(listener: (type: string, payload: string) => void): void;
}

interface Window {
  jetwhale: JetWhaleBridge;
}

interface WindowEventMap {
  "jetwhale:ready": Event;
}
