# JetWhale Plugin Development Guide

JetWhale is a Kotlin-based debugging tool with a plugin-based architecture. This guide explains how to develop JetWhale plugins.

## Architecture Overview

JetWhale plugins consist of three components:

| Component | Description | Runtime Environment |
|-----------|-------------|---------------------|
| **Protocol** | Event, Method, and MethodResult definitions | Shared (Agent/Host) |
| **Agent Plugin** | Plugin on the debuggee (application) side | Android/JVM/iOS/Web |
| **Host Plugin** | Plugin on the host (debugger) side | Desktop/Android |

```
┌─────────────────────────────────────────────────────────────────┐
│                        JetWhale Host                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    Host Plugin                            │  │
│  │  - onEvent(event: Event)     ← Receive events            │  │
│  │  - dispatch(method: Method)  → Send methods              │  │
│  │  - Content()                 ← Render UI                 │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────┘
                             │ WebSocket (JSON)
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Debuggee Application                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   Agent Plugin                            │  │
│  │  - enqueueEvent(event: Event)    → Send events           │  │
│  │  - onReceiveMethod(method: Method) ← Receive methods     │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Communication Flow

### Event Sending (Agent → Host)

1. Call `enqueueEvent(event)` on the Agent side
2. Protocol serializes the event
3. Send to Host via WebSocket
4. `onEvent(event)` is called on the Host side

### Method Invocation (Host → Agent)

1. Call `context.dispatch(method)` on the Host side
2. Protocol serializes the method
3. Send to Agent via WebSocket
4. `onReceiveMethod(method)` is called on the Agent side
5. Return the result to Host

## Plugin Development Workflow

### 1. Protocol Definition

First, define the data types shared between Agent and Host.

```kotlin
// Event: Agent → Host
@Serializable
sealed interface MyEvent {
    @Serializable
    data class StateChanged(val newState: String) : MyEvent
}

// Method: Host → Agent
@Serializable
sealed interface MyMethod {
    @Serializable
    data class UpdateState(val state: String) : MyMethod
}

// MethodResult: Agent → Host (response to Method)
@Serializable
sealed interface MyMethodResult {
    @Serializable
    data object Success : MyMethodResult

    @Serializable
    data class Error(val message: String) : MyMethodResult
}
```

Details: [Protocol Definition Guide](./protocol.md)

### 2. Agent Plugin Implementation

Implement the plugin on the debuggee application side.

```kotlin
class MyAgentPlugin : JetWhaleAgentPlugin<MyEvent, MyMethod, MyMethodResult>() {
    override val pluginId: String = "com.example.myplugin"
    override val pluginVersion: String = "1.0.0"
    override val protocol = kotlinxSerializationJetWhaleAgentPluginProtocol<MyEvent, MyMethod, MyMethodResult>()

    override suspend fun onReceiveMethod(method: MyMethod): MyMethodResult {
        return when (method) {
            is MyMethod.UpdateState -> {
                // Update state
                MyMethodResult.Success
            }
        }
    }
}
```

Details: [Agent Plugin Implementation Guide](./agent-plugin.md)

### 3. Host Plugin Implementation

Implement the plugin on the host (debugger) side.

```kotlin
@AutoService(JetWhaleHostPluginFactory::class)
class MyPluginFactory : JetWhaleHostPluginFactory {
    override val meta = jetWhalePluginMetaData(
        pluginId = "com.example.myplugin",
        pluginName = "My Plugin",
        version = "1.0.0",
    )

    override fun createPlugin() = MyHostPlugin()
}

class MyHostPlugin : JetWhaleHostPlugin<MyEvent, MyMethod, MyMethodResult>() {
    override val protocol = kotlinxSerializationJetWhaleHostPluginProtocol<MyEvent, MyMethod, MyMethodResult>()

    override fun onEvent(event: MyEvent) {
        // Handle event
    }

    @Composable
    override fun Content(context: JetWhaleDebugOperationContext<MyMethod, MyMethodResult>) {
        // Render UI
    }
}
```

Details: [Host Plugin Implementation Guide](./host-plugin.md)

## Plugin ID Convention

The `pluginId` is a string that uniquely identifies a plugin. Follow these conventions:

- Use reverse domain name notation (e.g., `com.example.myplugin`)
- Use the same `pluginId` for both Agent and Host
- Use lowercase letters only

## Example Project Structure

```
my-jetwhale-plugin/
├── protocol/                    # Protocol definitions (shared)
│   └── src/commonMain/kotlin/
│       ├── MyEvent.kt
│       ├── MyMethod.kt
│       └── MyMethodResult.kt
├── agent/                       # Agent Plugin
│   └── src/commonMain/kotlin/
│       └── MyAgentPlugin.kt
└── host/                        # Host Plugin
    └── src/main/kotlin/
        ├── MyHostPlugin.kt
        └── MyPluginUI.kt
```

## Dependencies

### Agent Plugin

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-sdk:$version")
    implementation("com.kitakkun.jetwhale:jetwhale-protocol-agent:$version")
}
```

### Host Plugin

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-host-sdk:$version")
    implementation("com.kitakkun.jetwhale:jetwhale-protocol-host:$version")

    // AutoService (optional)
    kapt("com.google.auto.service:auto-service:1.1.1")
    implementation("com.google.auto.service:auto-service-annotations:1.1.1")
}
```

## Next Steps

- [Protocol Definition Guide](./protocol.md) - How to define Event, Method, and MethodResult
- [Agent Plugin Implementation Guide](./agent-plugin.md) - Implementing debuggee-side plugins
- [Host Plugin Implementation Guide](./host-plugin.md) - Implementing host-side plugins
