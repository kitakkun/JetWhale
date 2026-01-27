# Protocol Definition Guide

This guide explains how to define the communication protocol between Agent and Host plugins.

## Overview

JetWhale plugins communicate using three types of messages:

| Type | Direction | Description |
|------|-----------|-------------|
| **Event** | Agent → Host | Notifications sent from the debuggee to the debugger |
| **Method** | Host → Agent | Requests sent from the debugger to the debuggee |
| **MethodResult** | Agent → Host | Responses to Method requests |

All message types are serialized to JSON using [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization).

> **Highly Recommended: Always use `@SerialName`**
>
> JetWhale uses `classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS`, which adds a `"type"` field to all JSON objects. Without `@SerialName`, the fully qualified class name (e.g., `com.example.MyEvent.StateChanged`) is used as the type discriminator. This creates a tight coupling between your class names/packages and the serialized protocol.
>
> **It is highly recommended to specify `@SerialName` on all sealed interface members** to:
> - Maintain protocol compatibility when refactoring (renaming classes, moving packages)
> - Keep JSON payloads clean and readable
> - Ensure binary compatibility between different versions of Agent and Host plugins

## Project Structure

Protocol types should be defined in a shared module that both Agent and Host plugins depend on:

```
my-plugin/
├── protocol/                    # Shared protocol module
│   ├── build.gradle.kts
│   └── src/commonMain/kotlin/
│       ├── MyEvent.kt
│       ├── MyMethod.kt
│       └── MyMethodResult.kt
├── agent/                       # Depends on protocol
│   └── ...
└── host/                        # Depends on protocol
    └── ...
```

## Defining Events

Events are messages sent from the Agent (debuggee) to the Host (debugger). Use events to notify the Host about state changes, user interactions, or other occurrences in the application.

### Basic Event Definition

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("my_event")
@Serializable
sealed interface MyEvent {

    @SerialName("state_changed")
    @Serializable
    data class StateChanged(
        val previousState: String,
        val newState: String,
    ) : MyEvent

    @SerialName("button_clicked")
    @Serializable
    data class ButtonClicked(
        val buttonId: String,
        val clickCount: Int,
    ) : MyEvent

    @SerialName("error_occurred")
    @Serializable
    data class ErrorOccurred(
        val errorMessage: String,
        val stackTrace: String? = null,
    ) : MyEvent
}
```

### Event Design Guidelines

- Use `sealed interface` for type-safe exhaustive handling
- Annotate with `@Serializable` for JSON serialization
- **Always use `@SerialName`** on all sealed interface members to ensure protocol stability (see warning above)
- Include all relevant data needed by the Host
- Use optional parameters with defaults for backward compatibility

## Defining Methods

Methods are requests sent from the Host (debugger) to the Agent (debuggee). Use methods to query state, trigger actions, or modify the application.

### Basic Method Definition

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("my_method")
@Serializable
sealed interface MyMethod {

    // Simple method with no parameters
    @SerialName("ping")
    @Serializable
    data object Ping : MyMethod

    // Method with parameters
    @SerialName("get_user")
    @Serializable
    data class GetUser(
        val userId: String,
    ) : MyMethod

    // Method with multiple parameters
    @SerialName("update_settings")
    @Serializable
    data class UpdateSettings(
        val theme: String,
        val fontSize: Int,
        val notifications: Boolean,
    ) : MyMethod

    // Method with complex parameters
    @SerialName("execute_action")
    @Serializable
    data class ExecuteAction(
        val action: ActionType,
        val parameters: Map<String, String> = emptyMap(),
    ) : MyMethod
}

@Serializable
enum class ActionType {
    @SerialName("refresh") REFRESH,
    @SerialName("reset") RESET,
    @SerialName("export") EXPORT,
}
```

### Method Design Guidelines

- Use `data object` for methods with no parameters
- Use `data class` for methods with parameters
- Keep method names action-oriented (verbs)
- Group related methods in the same sealed interface

## Defining Method Results

MethodResults are responses sent from the Agent back to the Host after processing a Method request.

### Basic MethodResult Definition

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("my_method_result")
@Serializable
sealed interface MyMethodResult {

    // Simple success response
    @SerialName("pong")
    @Serializable
    data object Pong : MyMethodResult

    // Success with data
    @SerialName("user_data")
    @Serializable
    data class UserData(
        val userId: String,
        val userName: String,
        val email: String,
    ) : MyMethodResult

    // Generic success
    @SerialName("success")
    @Serializable
    data object Success : MyMethodResult

    // Error response
    @SerialName("error")
    @Serializable
    data class Error(
        val code: Int,
        val message: String,
    ) : MyMethodResult

    // Not found response
    @SerialName("not_found")
    @Serializable
    data class NotFound(
        val resourceId: String,
    ) : MyMethodResult
}
```

### MethodResult Design Guidelines

- Always include error result types for proper error handling
- Return meaningful data in success results
- Use specific result types rather than generic ones when possible
- Consider including error codes for programmatic handling

## Serialization Configuration

### Using kotlinx.serialization

Add the serialization plugin and dependency:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") // or kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

### SerialName Annotation (Highly Recommended)

**It is highly recommended to use `@SerialName`** on all Event, Method, and MethodResult types.

JetWhale uses `classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS`, which means that without `@SerialName`, the **fully qualified class name** is used as the type discriminator:

```kotlin
// WITHOUT @SerialName (NOT RECOMMENDED)
@Serializable
data class ButtonClicked(val count: Int) : MyEvent
// JSON: {"type": "com.example.myplugin.MyEvent.ButtonClicked", "count": 42}
//       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//       Full class path is embedded - breaks if you rename or move the class!
```

With `@SerialName`, you control the discriminator value explicitly:

```kotlin
// WITH @SerialName (RECOMMENDED)
@SerialName("button_clicked")
@Serializable
data class ButtonClicked(val count: Int) : MyEvent
// JSON: {"type": "button_clicked", "count": 42}
//       ^^^^^^^^^^^^^^^^^^^^^
//       Stable identifier - safe to refactor class names/packages
```

This ensures:
- **Protocol stability**: Renaming classes or moving packages won't break compatibility
- **Cleaner JSON**: Shorter, more readable type discriminators
- **Version compatibility**: Agent and Host can evolve independently

### JSON Format

JetWhale uses polymorphic serialization with a discriminator field. The JSON format looks like:

```json
// Event
{
  "type": "button_clicked",
  "count": 42
}

// Method
{
  "type": "get_user",
  "userId": "user-123"
}

// MethodResult
{
  "type": "user_data",
  "userId": "user-123",
  "userName": "John Doe",
  "email": "john@example.com"
}
```

## Protocol Interfaces

JetWhale provides protocol interfaces for both Agent and Host sides.

### Agent Protocol

```kotlin
public interface JetWhaleAgentPluginProtocol<Event, Method, MethodResult> {
    fun decodeMethod(value: String): Method
    fun encodeMethodResult(value: MethodResult): String
    fun encodeEvent(value: Event): String
}
```

Usage in Agent Plugin:

```kotlin
override val protocol: JetWhaleAgentPluginProtocol<MyEvent, MyMethod, MyMethodResult> =
    kotlinxSerializationJetWhaleAgentPluginProtocol()
```

### Host Protocol

```kotlin
public interface JetWhaleHostPluginProtocol<Event, Method, MethodResult> {
    fun encodeMethod(value: Method): String
    fun decodeMethodResult(value: String): MethodResult
    fun decodeEvent(value: String): Event
}
```

Usage in Host Plugin:

```kotlin
override val protocol: JetWhaleHostPluginProtocol<MyEvent, MyMethod, MyMethodResult> =
    kotlinxSerializationJetWhaleHostPluginProtocol()
```

## Advanced Topics

### Custom JSON Configuration

If you need custom JSON settings, pass a configured `Json` instance:

```kotlin
val customJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = false
}

override val protocol = kotlinxSerializationJetWhaleAgentPluginProtocol<MyEvent, MyMethod, MyMethodResult>(
    json = customJson
)
```

### Nested Sealed Classes

You can nest sealed classes for complex hierarchies:

```kotlin
@SerialName("my_event")
@Serializable
sealed interface MyEvent {

    @Serializable
    sealed interface UserEvent : MyEvent {
        @SerialName("user_logged_in")
        @Serializable
        data class LoggedIn(val userId: String) : UserEvent

        @SerialName("user_logged_out")
        @Serializable
        data object LoggedOut : UserEvent
    }

    @Serializable
    sealed interface SystemEvent : MyEvent {
        @SerialName("system_error")
        @Serializable
        data class Error(val message: String) : SystemEvent
    }
}
```

### Enum Serialization

Use `@SerialName` on enum values for consistent JSON representation:

```kotlin
@Serializable
enum class Priority {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
}

@SerialName("task_created")
@Serializable
data class TaskCreated(
    val taskId: String,
    val priority: Priority,
) : MyEvent
```

### Optional and Default Values

Use default values for optional fields:

```kotlin
@SerialName("config_updated")
@Serializable
data class ConfigUpdated(
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),  // Default value
    val metadata: Map<String, String> = emptyMap(),    // Optional field
) : MyEvent
```

### Collections and Maps

kotlinx.serialization supports standard collections:

```kotlin
@SerialName("batch_update")
@Serializable
data class BatchUpdate(
    val items: List<Item>,                    // List
    val tags: Set<String>,                    // Set
    val properties: Map<String, String>,      // Map
) : MyMethod

@Serializable
data class Item(
    val id: String,
    val value: Int,
)
```

## Complete Example

```kotlin
// ===== MyEvent.kt =====
package com.example.myplugin.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("my_event")
@Serializable
sealed interface MyEvent {

    @SerialName("initialized")
    @Serializable
    data class Initialized(
        val version: String,
        val timestamp: Long,
    ) : MyEvent

    @SerialName("state_changed")
    @Serializable
    data class StateChanged(
        val oldState: AppState,
        val newState: AppState,
    ) : MyEvent

    @SerialName("action_performed")
    @Serializable
    data class ActionPerformed(
        val actionId: String,
        val success: Boolean,
        val duration: Long,
    ) : MyEvent
}

@Serializable
enum class AppState {
    @SerialName("idle") IDLE,
    @SerialName("loading") LOADING,
    @SerialName("ready") READY,
    @SerialName("error") ERROR,
}

// ===== MyMethod.kt =====
package com.example.myplugin.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("my_method")
@Serializable
sealed interface MyMethod {

    @SerialName("ping")
    @Serializable
    data object Ping : MyMethod

    @SerialName("get_state")
    @Serializable
    data object GetState : MyMethod

    @SerialName("set_state")
    @Serializable
    data class SetState(val state: AppState) : MyMethod

    @SerialName("perform_action")
    @Serializable
    data class PerformAction(
        val actionId: String,
        val params: Map<String, String> = emptyMap(),
    ) : MyMethod
}

// ===== MyMethodResult.kt =====
package com.example.myplugin.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("my_method_result")
@Serializable
sealed interface MyMethodResult {

    @SerialName("pong")
    @Serializable
    data object Pong : MyMethodResult

    @SerialName("current_state")
    @Serializable
    data class CurrentState(val state: AppState) : MyMethodResult

    @SerialName("success")
    @Serializable
    data object Success : MyMethodResult

    @SerialName("failure")
    @Serializable
    data class Failure(
        val errorCode: Int,
        val errorMessage: String,
    ) : MyMethodResult
}
```

## See Also

- [Overview](./overview.md) - Plugin architecture overview
- [Agent Plugin Implementation Guide](./agent-plugin.md) - Using protocol in Agent plugins
- [Host Plugin Implementation Guide](./host-plugin.md) - Using protocol in Host plugins
- [kotlinx.serialization Documentation](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md)
