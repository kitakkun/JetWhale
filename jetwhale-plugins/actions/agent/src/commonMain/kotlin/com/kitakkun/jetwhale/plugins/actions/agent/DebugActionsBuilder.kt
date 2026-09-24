package com.kitakkun.jetwhale.plugins.actions.agent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@DslMarker
annotation class DebugActionsDsl

/**
 * Declares debug actions:
 *
 * ```kotlin
 * actionsPlugin.register {
 *     action("Reset onboarding") {
 *         run { onboarding.reset() }
 *     }
 *     action<LoginAs>("Log in as") {
 *         description = "Signs in with a test account"
 *         options("email") { testAccounts.map { it.email } }
 *         run { args -> auth.login(args.email, args.password) }
 *     }
 *     group("Clock") {
 *         action<Shift>("Shift time") { run { clock.shift(it.minutes.minutes) } }
 *     }
 * }
 * ```
 *
 * An action's arguments are one `@Serializable` class: its properties become the fields of the
 * host's form and of the MCP tool's schema, and `@McpDescription` on a property documents it.
 */
@DebugActionsDsl
class DebugActionsBuilder internal constructor(private val group: String?) {
    internal val definitions = mutableListOf<DebugActionDefinition<*>>()

    /** Declares [content]'s actions inside a group named [name]; groups nest. */
    fun group(name: String, content: DebugActionsBuilder.() -> Unit) {
        val nested = DebugActionsBuilder(group = listOfNotNull(group, name).joinToString(" / "))
        nested.content()
        definitions += nested.definitions
    }

    /** Declares an action that takes no arguments. */
    // Erases to the same JVM signature as the reified overload below.
    @JvmName("actionWithoutArguments")
    fun action(title: String, configure: DebugActionBuilder<Unit>.() -> Unit) {
        action(title, Unit.serializer(), configure)
    }

    /** Declares an action whose arguments are decoded into [A]. */
    inline fun <reified A> action(title: String, noinline configure: DebugActionBuilder<A>.() -> Unit) {
        action(title, serializer<A>(), configure)
    }

    /** Declares an action whose arguments [argumentSerializer] decodes. */
    fun <A> action(title: String, argumentSerializer: KSerializer<A>, configure: DebugActionBuilder<A>.() -> Unit) {
        val builder = DebugActionBuilder<A>()
        builder.configure()
        definitions += builder.build(title = title, group = group, argumentSerializer = argumentSerializer)
    }
}

/**
 * Configures one action. Only [run] is required.
 *
 * @property destructive The action changes or discards something that cannot be restored. The host
 *   asks before running it, and an AI agent has to confirm explicitly.
 * @property runsOnMainThread Runs the action on `Dispatchers.Main`, for work that touches UI state.
 *   Otherwise it runs on a background dispatcher.
 * @property timeout How long a run may take before it is cancelled and reported as timed out.
 */
@DebugActionsDsl
class DebugActionBuilder<A> internal constructor() {
    var description: String? = null
    var destructive: Boolean = false
    var runsOnMainThread: Boolean = false
    var timeout: Duration = 30.seconds

    private val optionProviders = mutableMapOf<String, suspend () -> List<String>>()
    private var body: (suspend (A) -> Any?)? = null

    /**
     * Supplies suggested values for the argument property [parameter] — test accounts, feature
     * names — fetched each time the host asks, so they can come from the app's current state.
     */
    fun options(parameter: String, provider: suspend () -> List<String>) {
        optionProviders[parameter] = provider
    }

    /**
     * What the action does. Its return value is shown to whoever ran it: a `String` as text, a
     * `JsonElement` as JSON, anything else through `toString()`; `Unit` or `null` shows nothing.
     */
    fun run(block: suspend (A) -> Any?) {
        body = block
    }

    internal fun build(title: String, group: String?, argumentSerializer: KSerializer<A>): DebugActionDefinition<A> = DebugActionDefinition(
        title = title,
        group = group,
        description = description,
        destructive = destructive,
        runsOnMainThread = runsOnMainThread,
        timeout = timeout,
        argumentSerializer = argumentSerializer,
        optionProviders = optionProviders.toMap(),
        body = checkNotNull(body) { "the debug action '$title' has no run { } block" },
    )
}

/** A declared action, before it is registered and given an id. */
internal class DebugActionDefinition<A>(
    val title: String,
    val group: String?,
    val description: String?,
    val destructive: Boolean,
    val runsOnMainThread: Boolean,
    val timeout: Duration,
    val argumentSerializer: KSerializer<A>,
    val optionProviders: Map<String, suspend () -> List<String>>,
    val body: suspend (A) -> Any?,
)
