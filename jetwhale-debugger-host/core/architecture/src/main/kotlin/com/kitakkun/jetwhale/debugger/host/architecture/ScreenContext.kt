package com.kitakkun.jetwhale.debugger.host.architecture

interface ScreenContext

@ExplicitScreenContextUsage
inline fun withScreenContext(block: ScreenContext.() -> Unit) {
    val context = object : ScreenContext {}
    context.block()
}

@RequiresOptIn(
    message = "Using ScreenContext explicitly is discouraged. " +
        "Use context receivers instead to propagate ScreenContext implicitly." +
        "If you really need to use it explicitly, please annotate your usage with @ExplictScreenContextUsage.",
    level = RequiresOptIn.Level.WARNING,
)
annotation class ExplicitScreenContextUsage
