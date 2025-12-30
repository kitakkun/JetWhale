package com.kitakkun.jetwhale.debugger.host.model

@JvmInline
value class JetWhaleColorSchemeId private constructor(val id: String) {
    companion object {
        val BuiltInDynamic: JetWhaleColorSchemeId = builtin("dynamic")
        val BuiltInLight: JetWhaleColorSchemeId = builtin("light")
        val BuiltInDark: JetWhaleColorSchemeId = builtin("dark")

        val BuiltIns: List<JetWhaleColorSchemeId> = listOf(
            BuiltInDynamic,
            BuiltInLight,
            BuiltInDark,
        )

        private fun builtin(id: String): JetWhaleColorSchemeId = JetWhaleColorSchemeId("builtin:$id")
        fun custom(id: String): JetWhaleColorSchemeId = JetWhaleColorSchemeId("custom:$id")
    }
}