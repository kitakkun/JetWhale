package com.kitakkun.jetwhale.host.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The part of a GitHub release, as the releases API lists it, that the update lookup reads. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean,
    val assets: List<GitHubReleaseAsset>,
) {
    fun asset(name: String): GitHubReleaseAsset? = assets.firstOrNull { it.name == name }
}

@Serializable
internal data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
