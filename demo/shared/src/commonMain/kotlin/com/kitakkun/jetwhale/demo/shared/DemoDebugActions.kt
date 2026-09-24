package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.annotations.McpDescription
import com.kitakkun.jetwhale.plugins.actions.agent.JetWhaleDebugActionsAgentPlugin
import com.kitakkun.jetwhale.plugins.actions.agent.platformBuiltInActions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * The demo's app-wide debug actions, standing in for what a real app keeps in a debug menu: a
 * session to sign into, state to reset, a value to read back.
 */
internal fun registerDemoDebugActions(plugin: JetWhaleDebugActionsAgentPlugin) {
    plugin.register {
        platformBuiltInActions()
        group("Account") {
            action<SignIn>("Sign in as test user") {
                description = "Replaces the demo session with a test account."
                options("email") { TEST_ACCOUNTS }
                run { args ->
                    DemoSession.email = args.email
                    DemoSession.tier = args.tier
                    "Signed in as ${args.email} (${args.tier})"
                }
            }
            action("Current session") {
                description = "Returns the demo session as JSON."
                run {
                    buildJsonObject {
                        put("email", DemoSession.email)
                        put("tier", DemoSession.tier.name)
                        put("checkedAt", Clock.System.now().toString())
                    }
                }
            }
        }
        action("Wipe demo session") {
            description = "Signs out and forgets the session."
            destructive = true
            run {
                DemoSession.email = null
                DemoSession.tier = Tier.FREE
            }
        }
    }
}

private val TEST_ACCOUNTS = listOf("qa@example.com", "pro@example.com", "new-user@example.com")

@Serializable
private enum class Tier { FREE, PRO }

@Serializable
private data class SignIn(
    @McpDescription("The test account's address.")
    val email: String,
    val tier: Tier = Tier.FREE,
)

/** The demo has no real accounts; this is the state the actions above read and change. */
private object DemoSession {
    var email: String? = null
    var tier: Tier = Tier.FREE
}
