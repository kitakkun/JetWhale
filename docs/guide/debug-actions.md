# Debug Actions

Debug Actions turns an app's debug menu into typed actions the JetWhale host and AI agents can
run: sign in as a test user, reset onboarding, shift the clock, open a deep link. The app declares
each action once; the host builds a form for its arguments, and an agent gets the same action with
a JSON Schema over MCP.

## Setup

Install **Debug Actions** from **Settings → Plugins → Add Plugins → Official Plugins**, then add the
agent to the app:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-debug-actions-agent:<version>")
    // Only for actions that belong to a screen:
    implementation("com.kitakkun.jetwhale:jetwhale-debug-actions-agent-compose:<version>")
}
```

```kotlin
val actionsPlugin = JetWhaleDebugActionsAgentPlugin()

actionsPlugin.register {
    platformBuiltInActions()
    action("Reset onboarding") {
        run { onboarding.reset() }
    }
}

startJetWhale { plugins { register(actionsPlugin) } }
```

## Declaring actions

An action's arguments are one `@Serializable` class. Its properties become the fields of the
host's form and of the MCP schema; a property with a default may be left out, and
`@McpDescription` documents it for people and agents alike.

```kotlin
@Serializable
data class SignIn(
    @McpDescription("The test account's address.")
    val email: String,
    val tier: Tier = Tier.FREE,
)

actionsPlugin.register {
    group("Account") {
        action<SignIn>("Sign in as test user") {
            description = "Replaces the session with a test account."
            options("email") { testAccounts.map { it.email } }
            run { args -> auth.signIn(args.email, args.tier) }
        }
    }
}
```

| Setting | What it does |
|---|---|
| `description` | Shown under the title and to agents |
| `options(property) { … }` | Suggested values for a property, fetched from the app each time |
| `destructive = true` | The host asks before running; an agent must pass `confirmDestructive: true` |
| `runsOnMainThread = true` | Runs on `Dispatchers.Main`, for work that touches UI state |
| `timeout` | How long a run may take (30 seconds unless set) |

The value `run` returns is shown to whoever ran it: a `String` as text, a `JsonElement` as JSON,
anything else through `toString()`. A thrown exception is reported with its stack trace. An action
can therefore also answer a question — "what is the current user id?" — rather than change
anything.

Properties are entered by type: text for strings and numbers, a switch for a `Boolean`, a menu for
an `enum`, and JSON for anything else (lists, nested classes).

### Actions of a screen

With the Compose artifact, a composable registers actions that exist only while it is shown:

```kotlin
@Composable
fun CheckoutScreen(form: CheckoutFormState) {
    actionsPlugin.DebugActions(form) {
        action("Fill test card") { run { form.fill(TestCards.visa) } }
    }
}
```

The host marks them **Screen** and they appear and disappear as the user navigates. Pass what the
actions capture as keys; they are declared again when a key changes.

## Built-in actions

`platformBuiltInActions()` adds, in a **Built-in** group, actions that need no app code:

| Platform | Actions |
|---|---|
| Android | Restart app, Open deep link, Set dark mode (Android 12+), Set app language (Android 13+) |
| iOS, macOS, JVM | Open URL |
| Web | None — a page may open a URL only in response to a user gesture |

## In the host

- **Search** — ⌘K (Ctrl+K) focuses the search field; Enter picks the first match.
- **Pins** — pinned actions stay at the top of the list, across restarts.
- **Arguments** — the form starts from the arguments the action last ran with.
- **Runs** — the latest result under the form, and the action's recent runs, including those an AI
  agent made. A run in progress can be cancelled.

## MCP tools

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.actions.listActions` | Every action with its argument JSON Schema, suggested values, and whether it is destructive or belongs to the current screen |
| `com.kitakkun.jetwhale.actions.runAction` | Runs an action by id with arguments; returns the outcome, the result and any error with its stack trace |

The tool list of an MCP connection is fixed when it opens, while screen actions come and go, so
actions are not tools of their own: an agent lists them, then runs one by id. List again after
navigating.
