# Host Plugin Implementation Guide

This guide explains how to implement a Host Plugin that runs on the debugger (host) side.

## Overview

Host Plugins run within the JetWhale host application and are responsible for:

- Receiving events from the Agent (debuggee)
- Sending method calls to the Agent
- Rendering the plugin UI using Jetpack Compose

## Class Hierarchy

JetWhale provides the following classes for Host Plugins:

| Class | Description |
|-------|-------------|
| `JetWhaleRawHostPlugin` | Base class for raw string-based communication |
| `JetWhaleHostPlugin<Event, Method, MethodResult>` | Type-safe abstract class with protocol support |
| `JetWhaleHostPluginFactory` | Factory interface for plugin instantiation |

## JetWhaleHostPluginFactory

Every Host Plugin must provide a factory class for plugin discovery and instantiation.

### Factory Interface

```kotlin
public interface JetWhaleHostPluginFactory {
    // Plugin metadata (id, name, version)
    val meta: JetWhalePluginMetaData

    // Plugin icon (optional)
    val icon: JetWhalePluginIcon get() = unspecifiedPluginIcon()

    // Create plugin instance
    fun createPlugin(): JetWhaleRawHostPlugin
}
```

### Factory Implementation

```kotlin
@AutoService(JetWhaleHostPluginFactory::class)
class MyPluginFactory : JetWhaleHostPluginFactory {

    override val meta: JetWhalePluginMetaData = jetWhalePluginMetaData(
        pluginId = "com.example.myplugin",
        pluginName = "My Plugin",
        version = "1.0.0",
    )

    override val icon: JetWhalePluginIcon = pluginIcon(
        activeIconPath = "icons/my_plugin_active.svg",
        inactiveIconPath = "icons/my_plugin_inactive.svg",
    )

    override fun createPlugin(): JetWhaleHostPlugin<*, *, *> {
        return MyHostPlugin()
    }
}
```

### Plugin Metadata

```kotlin
public class JetWhalePluginMetaData(
    val pluginId: String,     // Unique identifier (must match Agent plugin)
    val pluginName: String,   // Display name in UI
    val version: String,      // Plugin version
)
```

### Plugin Icon

```kotlin
public class JetWhalePluginIcon(
    val activeIconPath: String?,    // Icon when plugin tab is active
    val inactiveIconPath: String?,  // Icon when plugin tab is inactive
)
```

Icon paths are relative to the plugin JAR's resources directory. Supported formats: SVG, PNG, JPG.

## JetWhaleHostPlugin

### Class Definition

```kotlin
public abstract class JetWhaleHostPlugin<Event, Method, MethodResult>
    : JetWhaleRawHostPlugin() {

    // Type-safe communication protocol
    protected abstract val protocol: JetWhaleHostPluginProtocol<Event, Method, MethodResult>

    // Handle events from the debuggee
    public abstract fun onEvent(event: Event)

    // Render plugin UI
    @Composable
    public abstract fun Content(context: JetWhaleDebugOperationContext<Method, MethodResult>)

    // Optional: Cleanup when plugin is disposed
    public open fun onDispose() {}
}
```

### Required Members

| Member | Type | Description |
|--------|------|-------------|
| `protocol` | `JetWhaleHostPluginProtocol` | Serialization protocol |
| `onEvent()` | `fun` | Handle incoming events from Agent |
| `Content()` | `@Composable` | Render the plugin UI |

## Implementation Steps

### Step 1: Define Protocol Types

Define shared Event, Method, and MethodResult types. See [Protocol Definition Guide](./protocol.md).

### Step 2: Create the Host Plugin Class

```kotlin
class MyHostPlugin : JetWhaleHostPlugin<MyEvent, MyMethod, MyMethodResult>() {

    // Use kotlinx.serialization for type-safe JSON conversion
    override val protocol: JetWhaleHostPluginProtocol<MyEvent, MyMethod, MyMethodResult> =
        kotlinxSerializationJetWhaleHostPluginProtocol()

    // State for UI
    private val eventLogs = mutableStateListOf<String>()
    private var currentState by mutableStateOf("Unknown")

    // Handle events from the Agent
    override fun onEvent(event: MyEvent) {
        when (event) {
            is MyEvent.StateChanged -> {
                currentState = event.newState
                eventLogs.add("State changed: ${event.newState}")
            }
            is MyEvent.ButtonClicked -> {
                eventLogs.add("Button clicked: ${event.buttonId}")
            }
        }
    }

    // Render plugin UI
    @Composable
    override fun Content(context: JetWhaleDebugOperationContext<MyMethod, MyMethodResult>) {
        MyPluginContent(
            eventLogs = eventLogs,
            currentState = currentState,
            context = context,
        )
    }

    // Optional: Cleanup resources
    override fun onDispose() {
        eventLogs.clear()
    }
}
```

### Step 3: Create the Factory Class

```kotlin
@AutoService(JetWhaleHostPluginFactory::class)
class MyPluginFactory : JetWhaleHostPluginFactory {
    override val meta = jetWhalePluginMetaData(
        pluginId = "com.example.myplugin",  // Must match Agent plugin
        pluginName = "My Plugin",
        version = "1.0.0",
    )

    override fun createPlugin() = MyHostPlugin()
}
```

### Step 4: Implement the UI

```kotlin
@Composable
fun MyPluginContent(
    eventLogs: List<String>,
    currentState: String,
    context: JetWhaleDebugOperationContext<MyMethod, MyMethodResult>,
) {
    val scope = context.coroutineScope

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Plugin") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Display current state
            Text("Current State: $currentState")

            // Button to send method to Agent
            Button(onClick = {
                scope.launch {
                    val result = context.dispatch(MyMethod.Ping)
                    when (result) {
                        is MyMethodResult.Pong -> {
                            // Handle pong response
                        }
                        else -> { /* Handle other results */ }
                    }
                }
            }) {
                Text("Send Ping")
            }

            // Display event logs
            LazyColumn {
                items(eventLogs) { log ->
                    Text(log)
                }
            }
        }
    }
}
```

## JetWhaleDebugOperationContext

The `Content()` function receives a context object for interacting with the Agent:

```kotlin
public interface JetWhaleDebugOperationContext<Method, MethodResult> {
    // CoroutineScope for launching async operations
    val coroutineScope: CoroutineScope

    // Send method to Agent and await result
    suspend fun dispatch(method: Method): MethodResult?
}
```

### Sending Methods

Use `context.dispatch()` to send methods to the Agent:

```kotlin
@Composable
fun Content(context: JetWhaleDebugOperationContext<MyMethod, MyMethodResult>) {
    val scope = context.coroutineScope

    Button(onClick = {
        scope.launch {
            // Send method and wait for result
            val result = context.dispatch(MyMethod.GetData(id = 123))

            when (result) {
                is MyMethodResult.DataResponse -> {
                    // Use the returned data
                    processData(result.data)
                }
                is MyMethodResult.Error -> {
                    // Handle error
                    showError(result.message)
                }
                null -> {
                    // No response (connection lost or timeout)
                }
            }
        }
    }) {
        Text("Fetch Data")
    }
}
```

## Plugin Discovery

Host Plugins are discovered using Java's Service Provider Interface (SPI).

### Using AutoService (Recommended)

Add the `@AutoService` annotation to your factory class:

```kotlin
@AutoService(JetWhaleHostPluginFactory::class)
class MyPluginFactory : JetWhaleHostPluginFactory {
    // ...
}
```

Add the AutoService dependency:

```kotlin
// build.gradle.kts
dependencies {
    kapt("com.google.auto.service:auto-service:1.1.1")
    implementation("com.google.auto.service:auto-service-annotations:1.1.1")
}
```

### Manual SPI Configuration

Alternatively, create the SPI configuration file manually:

1. Create file: `src/main/resources/META-INF/services/com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory`
2. Add the fully qualified class name of your factory:
   ```
   com.example.myplugin.MyPluginFactory
   ```

## JetWhaleRawHostPlugin

For advanced use cases requiring raw string handling, extend `JetWhaleRawHostPlugin`:

```kotlin
public abstract class JetWhaleRawHostPlugin {
    // Handle raw event messages
    abstract fun onRawEvent(event: String)

    // Cleanup when plugin is disposed
    open fun onDispose() {}

    // Render UI with raw string context
    @Composable
    abstract fun ContentRaw(context: JetWhaleDebugOperationContext<String, String>)
}
```

## Complete Example

```kotlin
// Factory
@AutoService(JetWhaleHostPluginFactory::class)
class ExamplePluginFactory : JetWhaleHostPluginFactory {
    override val meta = jetWhalePluginMetaData(
        pluginId = "com.kitakkun.jetwhale.example",
        pluginName = "Example Plugin",
        version = "1.0.0",
    )

    override val icon = pluginIcon(
        activeIconPath = "icons/window_filled.svg",
        inactiveIconPath = "icons/window_outlined.svg",
    )

    override fun createPlugin() = ExampleHostPlugin()
}

// Plugin
class ExampleHostPlugin : JetWhaleHostPlugin<ExampleEvent, ExampleMethod, ExampleMethodResult>() {

    override val protocol: JetWhaleHostPluginProtocol<ExampleEvent, ExampleMethod, ExampleMethodResult> =
        kotlinxSerializationJetWhaleHostPluginProtocol()

    private val eventLogs = mutableStateListOf<String>()

    override fun onEvent(event: ExampleEvent) {
        when (event) {
            is ExampleEvent.ButtonClicked -> {
                eventLogs.add("Button clicked: count=${event.count}")
            }
        }
    }

    @Composable
    override fun Content(context: JetWhaleDebugOperationContext<ExampleMethod, ExampleMethodResult>) {
        ExamplePluginUI(eventLogs = eventLogs, context = context)
    }

    override fun onDispose() {
        eventLogs.clear()
    }
}

// UI
@Composable
fun ExamplePluginUI(
    eventLogs: List<String>,
    context: JetWhaleDebugOperationContext<ExampleMethod, ExampleMethodResult>,
) {
    val scope = context.coroutineScope
    var pingResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Example Plugin") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            stickyHeader {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val result = context.dispatch(ExampleMethod.Ping)
                            pingResult = when (result) {
                                is ExampleMethodResult.Pong -> "Pong received!"
                                else -> "Unknown result"
                            }
                        }
                    }) {
                        Text("Send Ping")
                    }
                    pingResult?.let { Text(it) }
                }
            }

            items(eventLogs) { log ->
                Text(log, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
```

## Building and Distributing

### Build Configuration

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-host-sdk:$version")
    implementation("com.kitakkun.jetwhale:jetwhale-protocol-host:$version")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    kapt("com.google.auto.service:auto-service:1.1.1")
    implementation("com.google.auto.service:auto-service-annotations:1.1.1")
}

tasks.jar {
    // Include all dependencies in the JAR for plugin distribution
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

### Distribution

1. Build the plugin JAR
2. Place the JAR in the JetWhale host's plugins directory
3. The host will automatically discover and load the plugin

## Best Practices

1. **Use Compose state properly**: Use `mutableStateOf` and `mutableStateListOf` for reactive UI updates

2. **Handle null results**: `dispatch()` returns null if the connection is lost or the Agent doesn't respond

3. **Cleanup in onDispose()**: Release resources and clear state when the plugin is disposed

4. **Match plugin IDs**: Ensure `pluginId` matches between Agent and Host plugins

5. **Keep UI responsive**: Use `context.coroutineScope.launch` for async operations

6. **Provide meaningful icons**: Use distinct active/inactive icons for better UX

## See Also

- [Overview](./overview.md) - Plugin architecture overview
- [Protocol Definition Guide](./protocol.md) - Defining Event, Method, and MethodResult types
- [Agent Plugin Implementation Guide](./agent-plugin.md) - Implementing the Agent-side plugin
