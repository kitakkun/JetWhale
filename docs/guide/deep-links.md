# Deep Links

The Deep Links plugin lists the links the app you are debugging declares, lets you compose a link
and check it against those declarations, and opens it in the app — the way the platform would if the
link were followed from outside. An AI agent can do the same over MCP.

On Android and iOS it needs no configuration: the agent reads the app's own declarations.

**Works with:** Android, iOS and macOS; the JVM and the web with an opener of the app's own.

## Setup

### Install the host plugin

Install **Deep Links** from **Settings → Plugins → Add Plugins → Official Plugins**. To install it
by Maven coordinates or from a file, see [Host Settings → Plugins](/guide/host-settings#plugins).

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-deep-links-agent:<version>")
}
```

```kotlin
startJetWhale {
    plugins {
        register(JetWhaleDeepLinkAgentPlugin.platformDefaults())
    }
}
```

### What each platform reports

| Platform | Declared links | Opening a link |
|----------|----------------|----------------|
| Android | Every `<intent-filter>` with the `VIEW` action and a scheme, on every activity and activity alias: schemes, hosts, ports, `path` / `pathPrefix` / `pathSuffix` / `pathPattern` / `pathAdvancedPattern`, whether it is `BROWSABLE`, `autoVerify`, and on Android 12+ each host's App Links verification state | An `ACTION_VIEW` intent restricted to the app's own package, so a link another app also claims never opens a chooser. The result names the activity that handled it |
| iOS | Custom schemes from `CFBundleURLTypes` | `UIApplication.open`; an `https` link is opened as a universal link only, so it fails rather than falling back to Safari |
| macOS | Custom schemes from `CFBundleURLTypes` | `NSWorkspace`, which reaches the app only when it is the registered handler of the scheme |
| JVM, Web | Nothing to discover | Needs an opener of the app's own (see below) |

How the Android list is read: `PackageManager` cannot report intent filters for an installed app —
`GET_INTENT_FILTERS` is not supported for them and `ActivityInfo` carries none. The agent reads the
app's own compiled manifest in-process instead, which holds every filter exactly as the build merged
it, and resolves `@string/` references. It opens the base APK's manifest by its asset cookie
(`AssetManager.openXmlResourceParser(cookie, "AndroidManifest.xml")`): opened by name alone, the
manifest can come from a split APK, or from WebView once the app has shown one.

### Templates

Offer links the platform cannot list, or the ones testers use most, as templates. `{name}`
placeholders become form fields in the host:

```kotlin
JetWhaleDeepLinkAgentPlugin(
    templates = listOf(
        DeepLinkTemplate(name = "Product", template = "https://example.com/product/{id}", description = "Opens a product page"),
    ),
    opener = DeepLinkOpener.platformDefault(),
)
```

Values are percent-encoded, so a value with `/` or `?` stays one segment.

### Apps without a platform opener

On the JVM and the web there is no platform way to route a link into the running app, so pass an
opener that hands the link to the app's own router:

```kotlin
val opener = object : DeepLinkOpener {
    override val canOpen = true
    override suspend fun open(url: String): DeepLinkOpenResult {
        val routed = appRouter.navigate(url)
        return DeepLinkOpenResult(opened = routed, handledBy = emptyList(), error = if (routed) null else "no route for $url")
    }
}
```

## Using it

- **The catalog,** grouped by `scheme://host`, each declaration with its path matchers, the handling
  activity, and flags such as *not browsable* or the App Links verification state. Selecting one starts
  a link from its scheme, host and path.
- **A link builder.** Type a link, or fill in a template's fields. The builder checks the link against
  the declarations as you type and says which one it matches — or warns that none does, which means
  the platform will not send it to the app from outside even if the app would handle it.
- **Open in app,** with the result: whether it opened and which activity took it.
- **History** of opened links with their results, and **favorites** that persist.

## MCP tools

With the [MCP server](/guide/mcp-server) running, an AI agent can do the same through these tools.
Each takes the `sessionId` of the app's session.

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.deeplinks.listDeepLinks` | The declarations (each with a `sampleUrl` to start from), the templates, and notes on what the platform could not list |
| `com.kitakkun.jetwhale.deeplinks.openDeepLink` | Opens a link and reports whether it opened, which activities handled it, and which declarations it matches |

An agent can take a `sampleUrl` or fill a template, open it, and confirm the screen changed with the
[Nav3 Navigator](/guide/nav3-navigator) or the [Compose Semantics Inspector](/guide/compose-semantics-inspector).

## Limits

- **iOS universal links are not listed.** Associated domains live in the app's code signature, which
  an app cannot read about itself at runtime. Register them as [templates](#templates).
- **Links the app routes internally** without declaring them to the platform are not listed either.
