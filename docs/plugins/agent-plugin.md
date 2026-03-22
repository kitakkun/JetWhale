# Agent Plugin Implementation Guide

This guide explains how to implement an Agent Plugin that runs on the debuggee (application) side.

## Overview

Agent Plugins run within the debuggee application and are responsible for:

- Sending events to the Host (debugger)
- Receiving and handling method calls from the Host
- Returning method results to the Host

## Class Hierarchy

JetWhale provides two base classes for Agent Plugins:

| Class                                              | Description                                    |
|----------------------------------------------------|------------------------------------------------|
| `JetWhaleRawAgentPlugin`                           | Base class for raw string-based communication  |
| `JetWhaleAgentPlugin<Event, Method, MethodResult>` | Type-safe abstract class with protocol support |

For most use cases, extend `JetWhaleAgentPlugin` for type-safe communication.

## JetWhaleAgentPlugin

### Class Definition

```kotlin
public abstract class JetWhaleAgentPlugin<Event, Method, MethodResult>
    : JetWhaleRawAgentPlugin() {

    // Type-safe communication protocol
    protected abstract val protocol: JetWhaleAgentPluginProtocol<Event, Method, MethodResult>

    // Handle method requests from the debugger
    public abstract suspend fun onReceiveMethod(method: Method): MethodResult?

    // Optional: Hook for event queueing (e.g., logging)
    public open fun onEnqueueEvent(event: Event) {}

    // Send event to the debugger
    public fun enqueueEvent(event: Event)
}
```

### Required Properties and Methods

| Member              | Type                          | Description                                        |
|---------------------|-------------------------------|----------------------------------------------------|
| `pluginId`          | `String`                      | Unique plugin identifier (reverse domain notation) |
| `pluginVersion`     | `String`                      | Plugin version string                              |
| `protocol`          | `JetWhaleAgentPluginProtocol` | Serialization protocol                             |
| `onReceiveMethod()` | `suspend fun`                 | Handle incoming method calls                       |

## Implementation Steps

### Step 1: Define Protocol Types

First, define the Event, Method, and MethodResult types in a shared module.
See [Protocol Definition Guide](./protocol.md) for details.

### Step 2: Create the Agent Plugin Class

```kotlin
class MyAgentPlugin : JetWhaleAgentPlugin<MyEvent, MyMethod, MyMethodResult>() {

    // Unique plugin identifier - must match Host plugin
    override val pluginId: String = "com.example.myplugin"

    // Plugin version
    override val pluginVersion: String = "1.0.0"

    // Use kotlinx.serialization for type-safe JSON conversion
    override val protocol: JetWhaleAgentPluginProtocol<MyEvent, MyMethod, MyMethodResult> =
        kotlinxSerializationJetWhaleAgentPluginProtocol()

    // Handle method calls from the Host
    override suspend fun onReceiveMethod(method: MyMethod): MyMethodResult {
        return when (method) {
            is MyMethod.Ping -> MyMethodResult.Pong
            is MyMethod.GetState -> MyMethodResult.StateResponse(currentState)
            is MyMethod.UpdateValue -> {
                updateValue(method.newValue)
                MyMethodResult.Success
            }
        }
    }
}
```

### Step 3: Register the Plugin

Register your plugin using the JetWhale DSL:

```kotlin
// In your application initialization code
startJetWhale {
    connection {
        host = "localhost"  // Host address
        port = 8080         // Port number
    }
    plugins {
        register(MyAgentPlugin())
    }
}
```

## Sending Events

Use `enqueueEvent()` to send events to the Host:

```kotlin
class MyAgentPlugin : JetWhaleAgentPlugin<MyEvent, MyMethod, MyMethodResult>() {
    // ... required overrides ...

    // Call this method to send events
    fun notifyStateChanged(newState: String) {
        enqueueEvent(MyEvent.StateChanged(newState))
    }

    fun notifyButtonClicked(buttonId: String) {
        enqueueEvent(MyEvent.ButtonClicked(buttonId))
    }
}
```

### Event Queueing Behavior

- Events are queued if the WebSocket connection is not established
- Events are automatically flushed when the connection is established
- The queue buffer size defaults to 100 (oldest events are dropped when exceeded)
- Override `queueBufferSize()` to customize the buffer size

```kotlin
override fun queueBufferSize(): Int = 200  // Increase buffer size
```

## Handling Method Calls

The `onReceiveMethod()` function is called when the Host sends a method request:

```kotlin
override suspend fun onReceiveMethod(method: MyMethod): MyMethodResult {
    return when (method) {
        is MyMethod.Ping -> {
            // Simple response
            MyMethodResult.Pong
        }

        is MyMethod.GetData -> {
            // Return data from the application
            val data = fetchData()
            MyMethodResult.DataResponse(data)
        }

        is MyMethod.PerformAction -> {
            // Perform an action and return result
            try {
                performAction(method.actionId)
                MyMethodResult.Success
            } catch (e: Exception) {
                MyMethodResult.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

### Returning Null

Return `null` if the method should not return a result:

```kotlin
override suspend fun onReceiveMethod(method: MyMethod): MyMethodResult? {
    return when (method) {
        is MyMethod.FireAndForget -> {
            doSomething()
            null  // No response sent
        }
        else -> handleMethod(method)
    }
}
```

## Event Logging Hook

Override `onEnqueueEvent()` to add logging or additional processing when events are sent:

```kotlin
override fun onEnqueueEvent(event: MyEvent) {
    Log.d("MyPlugin", "Sending event: $event")

    // Update internal state for debugging
    eventHistory.add(event)
}
```

## JetWhaleRawAgentPlugin

For advanced use cases requiring raw string handling, extend `JetWhaleRawAgentPlugin`:

```kotlin
abstract class JetWhaleRawAgentPlugin {
    // Plugin identifier
    abstract val pluginId: String

    // Plugin version
    abstract val pluginVersion: String

    // Handle raw method messages
    abstract suspend fun onRawMethod(message: String): String?

    // Send raw event message
    fun enqueueRawEvent(message: String)
}
```

### Raw Plugin Example

```kotlin
class MyRawPlugin : JetWhaleRawAgentPlugin() {
    override val pluginId = "com.example.rawplugin"
    override val pluginVersion = "1.0.0"

    override suspend fun onRawMethod(message: String): String? {
        // Parse JSON manually
        val json = Json.parseToJsonElement(message)
        // Process and return raw JSON string
        return """{"status": "ok"}"""
    }
}
```

## Complete Example

```kotlin
class ExampleAgentPlugin : JetWhaleAgentPlugin<ExampleEvent, ExampleMethod, ExampleMethodResult>() {

    override val pluginId: String = "com.kitakkun.jetwhale.example"
    override val pluginVersion: String = "1.0.0"

    override val protocol: JetWhaleAgentPluginProtocol<ExampleEvent, ExampleMethod, ExampleMethodResult> =
        kotlinxSerializationJetWhaleAgentPluginProtocol()

    private val _eventLogsFlow = MutableStateFlow<List<String>>(emptyList())
    val eventLogsFlow: StateFlow<List<String>> = _eventLogsFlow.asStateFlow()

    override suspend fun onReceiveMethod(method: ExampleMethod): ExampleMethodResult {
        return when (method) {
            is ExampleMethod.Ping -> ExampleMethodResult.Pong
        }
    }

    override fun onEnqueueEvent(event: ExampleEvent) {
        _eventLogsFlow.update { it + "Event: $event" }
    }

    fun sendButtonClickedEvent(count: Int) {
        enqueueEvent(ExampleEvent.ButtonClicked(count))
    }
}
```

## Best Practices

1. **Use type-safe plugins**: Prefer `JetWhaleAgentPlugin` over `JetWhaleRawAgentPlugin` for
   compile-time safety

2. **Keep method handlers fast**: `onReceiveMethod()` runs on the message processing thread; offload
   heavy work to coroutines

3. **Use meaningful plugin IDs**: Follow reverse domain notation and keep IDs consistent between
   Agent and Host

4. **Handle errors gracefully**: Return error results instead of throwing exceptions in
   `onReceiveMethod()`

5. **Consider buffer size**: If your application generates many events rapidly, increase the queue
   buffer size

## See Also

- [Overview](./overview.md) - Plugin architecture overview
- [Protocol Definition Guide](./protocol.md) - Defining Event, Method, and MethodResult types
- [Host Plugin Implementation Guide](./host-plugin.md) - Implementing the Host-side plugin
