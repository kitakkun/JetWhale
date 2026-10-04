package com.kitakkun.jetwhale.host.release

private val SYSTEM_PROPERTY = Regex("""-D[^=\s]+(=.*)?""")
private val MAX_HEAP = Regex("""-Xmx\d+[kKmMgG]?""")
private val VALUED_OPTIONS = listOf("--add-opens=", "--add-exports=", "--enable-native-access=", "-Xdock:name=")

/**
 * Whether a release may ask the launcher for [argument]. Only the forms the launcher contract names
 * pass: `-D…`, `--add-opens=…`, `--add-exports=…`, `--enable-native-access=…`, `-Xdock:name=…` and
 * `-Xmx…`. Agents, `-XX:OnError`-style hooks, argument files and class-path options are refused, so
 * what a release can ask of the launcher stays explicit.
 */
fun isAllowedHostJvmArgument(argument: String): Boolean = SYSTEM_PROPERTY.matches(argument) ||
    MAX_HEAP.matches(argument) ||
    VALUED_OPTIONS.any { argument.startsWith(it) && argument.length > it.length }
