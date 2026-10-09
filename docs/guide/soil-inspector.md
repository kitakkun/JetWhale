# Soil Inspector

The Soil Inspector shows what the [Soil](https://github.com/soil-kt/soil) cache of the app you are
debugging holds: its queries, infinite queries, mutations and subscriptions, each with its state as
Soil keeps it, its options and its value. From the host or from an AI agent over MCP, you can
invalidate a query, resume a query or subscription, or remove an inactive entry from the cache.

**Works with:** Android, iOS, desktop (JVM) and the web, the platforms Soil supports.

![The Soil Inspector: the cache grouped into queries, infinite queries, mutations and subscriptions, each with its status badges, beside a stale query with its actions, state, value and options](../images/soil-inspector/cache-light.webp){.light-only width=688}
![The Soil Inspector: the cache grouped into queries, infinite queries, mutations and subscriptions, each with its status badges, beside a stale query with its actions, state, value and options](../images/soil-inspector/cache-dark.webp){.dark-only width=688}

::: warning Built against one Soil version
Soil has no public API for listing its cache, so the agent reads it through Soil's internal API.
That API can change in any Soil release, so the agent is built and tested against one Soil version,
currently **1.0.0-alpha15**. Use the same version in your app.
:::

## Setup

### Install the host plugin

Install **Soil Inspector** from **Settings → Plugins → Add Plugins → Official Plugins**. To install
it by Maven coordinates or from a file, see [Host Settings → Plugins](/guide/host-settings#plugins).

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-soil-inspector-agent:<version>")
}
```

Hand the plugin the client your app gives `SwrClientProvider`, and the policy you built it with:

```kotlin
val policy = SwrCachePolicy(coroutineScope = SwrCacheScope())
val swrClient = SwrCache(policy)

startJetWhale {
    connection { /* ... */ }
    plugins {
        register(JetWhaleSoilAgentPlugin(swrClient, policy))
    }
}
```

`SwrCachePlus` with a `SwrCachePlusPolicy` works the same way and adds the subscriptions.

| You pass | The inspector shows |
|----------|---------------------|
| The client and its policy | Every entry: those in use, and those Soil keeps cached after their screen left, until their `gcTime` runs out |
| The client, and `null` for the policy | Active entries only, read on `Dispatchers.Main` |
| A client that wraps Soil's own | Nothing: it says the client is unsupported, since only `SwrCache` and `SwrCachePlus` can be read |

### Values

The agent encodes an entry's value with the serializer of the value's own class, walking into
lists, sets, arrays, maps, pairs and infinite-query chunks. That covers any `@Serializable` class,
generic ones such as `Page<User>` included, and the built-in types. A value holding a class with no
serializer is shown with `toString()`. To show it as JSON, or to show any value in a shape of your
own, register a serializer by namespace or by id class:

```kotlin
JetWhaleSoilAgentPlugin(
    client = swrClient,
    policy = policy,
    valueSerializers = SoilValueSerializers {
        namespace("users/avatar", AvatarSerializer)
        idClass<GetReceiptKey.Id>(ReceiptSerializer)
    },
)
```

A registered serializer describes the value one fetch returns, so for an infinite query it is the
serializer of one chunk's data. The detail pane says which way each value was encoded.

## Using it

- **The cache, live.** Entries grouped by kind, with badges for the status, **Fetching**,
  **Validating**, **Paused**, **Stale**, **Invalidated**, **Inactive** and **Observed** (a screen is
  attached). Search by namespace or tag.
- **State.** Every field of the entry's state under Soil's own name, with times relative to the
  app's clock: `0` reads as initial or preloaded data, and an infinite `staleTime` as never.
- **Value.** Read from the app when you select the entry, and again when its reply changes, shown
  as a JSON tree or as JSON text.
- **Actions.** **Invalidate**, **Resume** and **Remove**, each disabled where it does not apply,
  with the reason underneath.
- **Mutations stay visible.** Soil drops a mutation once its screen leaves; the inspector keeps the
  last state it saw, marked **Gone**.

## MCP tools

With the [MCP server](/guide/mcp-server) running, an AI agent can do the same through these tools.
Each takes the `sessionId` of the app's session; entries are named by the `handle` that
`listEntries` reports.

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.soil.listEntries` | Every entry with its state, location, options and whether it is stale; narrows by `kind`, `namespace` or `status` |
| `com.kitakkun.jetwhale.soil.getEntry` | One entry with its value, how the value was encoded, and which actions apply |
| `com.kitakkun.jetwhale.soil.invalidateEntry` | Invalidates a query or infinite query, active or inactive |
| `com.kitakkun.jetwhale.soil.resumeEntry` | Resumes a query or subscription a screen observes |
| `com.kitakkun.jetwhale.soil.removeInactiveEntry` | Removes an inactive query or subscription from the cache |

## Limits

- **It never removes an active entry.** Removing one while a screen holds it would leave that screen
  with nothing to observe, so only cached, inactive entries can be removed, and the agent checks
  again just before removing one.
- **It never writes data.** There is no way to set an entry's value, or to purge or garbage-collect
  the whole cache.
- **Resume reaches observed entries only.** Soil hands a resume to the screens that observe an entry,
  so an entry nothing observes ignores it.
- **A value is cut at 256 K characters.** The detail pane says when one was.
- **Additions and removals take up to half a second to show.** Soil announces neither, so the agent
  reads its stores twice a second. A change to an active entry's state shows within a moment.
