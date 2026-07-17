package com.kitakkun.jetwhale.host.model

/**
 * Represents Maven artifact coordinates (groupId:artifactId:version)
 */
data class MavenCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repositoryUrl: String = MAVEN_CENTRAL_URL,
) {
    companion object {
        const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2"

        /**
         * Parse Maven coordinates from a string in the format "groupId:artifactId:version"
         * Optionally with repository URL: "groupId:artifactId:version@repositoryUrl"
         */
        fun parse(coordinates: String): MavenCoordinates {
            val (coordPart, repoUrl) = if (coordinates.contains("@")) {
                val parts = coordinates.split("@", limit = 2)
                parts[0] to parts[1]
            } else {
                coordinates to MAVEN_CENTRAL_URL
            }

            val parts = coordPart.split(":")
            require(parts.size == 3) {
                "Invalid Maven coordinates format. Expected 'groupId:artifactId:version', got: $coordinates"
            }
            return MavenCoordinates(
                groupId = parts[0],
                artifactId = parts[1],
                version = parts[2],
                repositoryUrl = repoUrl,
            )
        }
    }

    /**
     * Builds the URL to download the JAR file from the Maven repository
     */
    fun toJarUrl(): String {
        val groupPath = groupId.replace('.', '/')
        return "$repositoryUrl/$groupPath/$artifactId/$version/$artifactId-$version.jar"
    }

    override fun toString(): String = "$groupId:$artifactId:$version"
}
