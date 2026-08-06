package com.kitakkun.jetwhale.agent.gradle

/**
 * The Kotlin versions the compiler plugin is built and tested against.
 *
 * One artifact serves the whole range — it only touches API that every version in it agrees on, and
 * CI compiles a consumer with the shipped JAR on each. Outside the range there is no such evidence,
 * and the failure mode is a linkage error from deep inside someone else's compile, so the range is
 * checked up front where it can be explained.
 *
 * Keep in step with the matrix in `.github/workflows/agent-compiler-plugin-matrix.yml`.
 */
internal object SupportedKotlinVersions {
    /**
     * Below this the plugin cannot work at all: `CompilerPluginRegistrar.pluginId` is abstract from
     * 2.3 and absent before it, so a registrar written for one side fails to load on the other.
     */
    val MINIMUM: KotlinMinor = KotlinMinor(2, 3)

    /** Highest minor CI proves. Newer may well work — it simply has not been shown to. */
    val HIGHEST_TESTED: KotlinMinor = KotlinMinor(2, 4)
}

/** A Kotlin `major.minor`, which is the granularity the compiler plugin API breaks at. */
internal data class KotlinMinor(val major: Int, val minor: Int) : Comparable<KotlinMinor> {
    override fun compareTo(other: KotlinMinor): Int = compareValuesBy(this, other, KotlinMinor::major, KotlinMinor::minor)

    override fun toString(): String = "$major.$minor"
}

/**
 * Reads `major.minor` out of a Kotlin version string, tolerating everything that trails it.
 *
 * Versions in the wild are not all `2.4.10`: dev builds (`2.4.20-dev-1762`), IDE-tagged builds
 * (`2.3.20-ij253-105`) and the placeholder majors Android Studio reports (`2.3.255-dev-255`) all
 * appear. Only the first two numbers are needed, and null for anything unparseable is the right
 * answer — an unrecognised version is a reason to stay quiet, not to guess.
 */
internal fun parseKotlinMinor(version: String): KotlinMinor? {
    val parts = version.split('.')
    if (parts.size < 2) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
    return KotlinMinor(major, minor)
}

internal sealed interface KotlinSupport {
    /** Within the tested range. */
    data object Supported : KotlinSupport

    /** Older than the plugin can work with at all. */
    data class TooOld(val found: KotlinMinor) : KotlinSupport

    /** Newer than anything CI proves. Might work; nobody has shown it does. */
    data class Untested(val found: KotlinMinor) : KotlinSupport

    /** Version string in a shape this plugin does not recognise. */
    data object Unknown : KotlinSupport
}

internal fun kotlinSupportFor(version: String): KotlinSupport {
    val found = parseKotlinMinor(version) ?: return KotlinSupport.Unknown
    return when {
        found < SupportedKotlinVersions.MINIMUM -> KotlinSupport.TooOld(found)
        found > SupportedKotlinVersions.HIGHEST_TESTED -> KotlinSupport.Untested(found)
        else -> KotlinSupport.Supported
    }
}

/** What to tell the user, or null when there is nothing worth saying. */
internal fun KotlinSupport.message(): String? = when (this) {
    KotlinSupport.Supported, KotlinSupport.Unknown -> null

    is KotlinSupport.TooOld ->
        "The JetWhale agent plugin needs Kotlin ${SupportedKotlinVersions.MINIMUM} or newer, but this " +
            "build uses $found. The compiler plugin cannot load on it, so buildMachineWss() would fail " +
            "the compilation rather than degrade. Upgrade Kotlin, or write the address out with wss()."

    is KotlinSupport.Untested ->
        "The JetWhale agent plugin is tested up to Kotlin ${SupportedKotlinVersions.HIGHEST_TESTED}, " +
            "and this build uses $found. It may work unchanged — the compiler plugin API is only known " +
            "to break at minor versions, not guaranteed to. If the compilation fails to load the " +
            "plugin, drop the plugin and write the address out with wss() until JetWhale catches up."
}
