package com.kitakkun.jetwhale.host.model

data class WellKnownMavenRepository(
    val displayName: String,
    val url: String,
)

/** Repositories offered as presets in the Maven install dialog. */
object WellKnownMavenRepositories {
    val entries: List<WellKnownMavenRepository> = listOf(
        WellKnownMavenRepository("Maven Central", MavenCoordinates.MAVEN_CENTRAL_URL),
        WellKnownMavenRepository("Central Snapshots", MavenCoordinates.MAVEN_SNAPSHOTS_URL),
        WellKnownMavenRepository("Google", "https://dl.google.com/dl/android/maven2"),
        WellKnownMavenRepository("JitPack", "https://jitpack.io"),
    )

    fun matching(url: String): WellKnownMavenRepository? = entries.firstOrNull { it.url == url.trim().trimEnd('/') }
}
