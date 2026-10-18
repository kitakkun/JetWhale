package com.kitakkun.jetwhale.protocol.gradle

/**
 * When a wire schema needs a new protocol revision. The first dump is at revision 1, the wire a
 * plugin has when it adopts revisions. After it, a breaking change needs a higher revision than the
 * previous dump's, any change may come with one, and a revision never goes down.
 */
internal object ProtocolRevisionRule {
    const val UNSEEN_CHANGES_NOTE: String =
        "The check cannot see which side sends a message, so a change that only one side sends still counts as breaking. " +
            "It cannot see a change of meaning either, such as a default that now means something else or a custom " +
            "serializer that writes its value differently: raise protocolRevision for one by hand."

    /** Why [next] cannot follow [previous], the dump before it, or `null` when it can. */
    fun refusalOf(previous: WireSchema?, next: WireSchema): String? {
        if (previous == null) {
            return if (next.revision == 1) {
                null
            } else {
                "The first wire schema dump is at protocol revision 1, the wire the plugin has when it adopts revisions, " +
                    "but protocolRevision is ${next.revision}."
            }
        }
        if (next.revision < previous.revision) {
            return "protocolRevision went down from ${previous.revision} to ${next.revision}. A protocol revision never goes down."
        }
        if (next.revision > previous.revision) return null
        val breakingChanges = WireSchemaClassifier(previous, next).classifyChanges().filter { it.isBreaking }
        if (breakingChanges.isEmpty()) return null
        return buildString {
            appendLine("${breakingChanges.size} breaking change(s) since the dump at protocol revision ${previous.revision}:")
            breakingChanges.forEach { appendLine("  ${it.path}: ${it.description}") }
            appendLine("Set protocolRevision to ${previous.revision + 1} and run dumpWireSchema.")
            append(UNSEEN_CHANGES_NOTE)
        }
    }
}
