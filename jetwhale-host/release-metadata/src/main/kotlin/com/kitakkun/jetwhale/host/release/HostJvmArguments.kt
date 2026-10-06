package com.kitakkun.jetwhale.host.release

private val SYSTEM_PROPERTY = Regex("""-D([^=\s]+)(?:=(.*))?""")
private val MAX_HEAP = Regex("""-Xmx\d+[kKmMgG]?""")
private val VALUED_OPTIONS = listOf("--add-opens=", "--add-exports=", "--enable-native-access=")

/**
 * Whether a release may ask the launcher for [argument]. Only the forms the launcher contract names
 * pass: `-D…`, `--add-opens=…`, `--add-exports=…`, `--enable-native-access=…` and `-Xmx…`. Agents,
 * `-XX:OnError`-style hooks, argument files and class-path options are refused, so what a release can
 * ask of the launcher stays explicit.
 */
fun isAllowedHostJvmArgument(argument: String): Boolean = SYSTEM_PROPERTY.matches(argument) ||
    MAX_HEAP.matches(argument) ||
    VALUED_OPTIONS.any { argument.startsWith(it) && argument.length > it.length }

/**
 * The system property a `-Dkey=value` argument sets, as its key and value, or null for any other
 * argument. `-Dkey` alone sets an empty value, as `java` does.
 */
fun systemPropertyOf(argument: String): Pair<String, String>? {
    val match = SYSTEM_PROPERTY.matchEntire(argument) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}
