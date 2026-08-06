# Nav3 Navigator

The Nav3 Navigator shows the [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
back stack of the app you are debugging, and lets you drive it: push a screen, pop back, reorder
entries, or replace the whole stack with an exact state — from the host UI, or from an AI agent over
MCP.

It is generic: it knows nothing about your screens. Everything it can show and construct is derived
from the serializers your app **already** gives `rememberNavBackStack`.

## Install the host plugin

The Nav3 Navigator is in the host's **official catalog**: open **Settings → Plugins → Add Plugins →
Official Plugins** and install it with one click — no coordinates needed. See
[Host Settings → Plugins](/guide/host-settings#plugins) for the other install routes.

## Add it to your app

The agent side is a normal dependency of the app being debugged, alongside the agent runtime that
`startJetWhale` lives in:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-nav3-agent:<version>")
}
```

A Navigation 3 app already declares how its `NavKey`s serialize, because saved state needs it. Hand
the plugin that same declaration:

```kotlin
// The module you already pass to rememberNavBackStack.
val navKeyModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Home::class, Home.serializer())
        subclass(Detail::class, Detail.serializer())
    }
}

val nav3Plugin = JetWhaleNav3AgentPlugin(Nav3KeyCodec.openPolymorphic(navKeyModule))

startJetWhale {
    connection {
        endpoints {
            ws("localhost", 5080)
        }
    }
    plugins {
        register(nav3Plugin)
    }
}
```

Then mark the back stack you want the host to see:

```kotlin
@Composable
fun App() {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration { serializersModule = navKeyModule },
        Home,
    )

    nav3Plugin.TrackNavBackStack(backStack)

    NavDisplay(backStack = backStack, entryProvider = entryProvider { /* … */ })
}
```

That is the whole integration. `TrackNavBackStack` mirrors the stack to the host for as long as it is
in composition, so put it where the back stack is created — for a single-stack app, at the root, so
the host can navigate whatever screen the app is on.

::: tip Closed (sealed) hierarchies
If your keys are a sealed hierarchy rather than open polymorphism, build the codec from its own
serializer instead — no module needed:

```kotlin
JetWhaleNav3AgentPlugin(Nav3KeyCodec.closedPolymorphic(Screen.serializer()))
```
:::

### Several back stacks

An app that nests navigation registers each stack under its own id, and the host lets you pick:

```kotlin
nav3Plugin.TrackNavBackStack(mainBackStack)                      // "main"
nav3Plugin.TrackNavBackStack(sheetBackStack, stackId = "sheet")
```

## What you get in the host

- **The stack, live.** Every entry with its index, type, and `toString()`; the last one is marked
  `current`. It updates as the app navigates, whoever navigated it.
- **Per-entry actions.** *Pop to here*, *To top*, *Remove* — including removing an entry from the
  middle, which rewrites where "back" will land without leaving the current screen.
- **Push a NavKey.** The right-hand pane lists the key types the app can construct, each with its
  fields and a ready-to-fill JSON template. Click one, edit the values, and *Push* — or *Replace
  stack* to make it the only entry.
- **Copy to editor.** Any entry's key can be copied into the editor and pushed again, which also
  covers key types that are not in the catalog.

## MCP tools

With the [MCP server](./mcp-server) running, the same operations are available to an AI agent:

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.nav3.getBackStack` | The current stack(s), indexed, with each entry's JSON key |
| `com.kitakkun.jetwhale.nav3.listNavKeyTypes` | The constructible key types, with fields and templates |
| `com.kitakkun.jetwhale.nav3.pushNavKey` | Navigate to a key (optionally inserting it below the top) |
| `com.kitakkun.jetwhale.nav3.popBackStack` | Go back — by a count, or down to an index |
| `com.kitakkun.jetwhale.nav3.removeNavKeyAt` | Drop one entry from the middle |
| `com.kitakkun.jetwhale.nav3.moveNavKeyToTop` | Bring an entry already on the stack back to the top |
| `com.kitakkun.jetwhale.nav3.replaceBackStack` | Put the app into an exact state in one step |

`stackId` may be omitted whenever the app has a single back stack.

A typical agent flow is `listNavKeyTypes` → fill a template → `pushNavKey`, then `getBackStack` to
confirm. Every mutating tool returns the resulting stack, so the confirmation is usually already
in hand.

## What it refuses to do

- **Leaving the stack empty.** `NavDisplay` cannot render an empty back stack, so an operation that
  would empty it is rejected and nothing is applied — the app keeps running and the caller gets a
  message.
- **Inventing keys.** A key is decoded by the *app*, with the app's serializers. A type the app does
  not know is reported as an error rather than guessed at.
- **Half-applying.** Operations are validated as a unit: an out-of-range index or an undecodable key
  leaves the app's back stack exactly as it was.

Entries that survive an edit keep their identity, so Navigation 3 keeps the saved state and
ViewModels behind the screens you did not touch.
