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

        private val GAV_REGEX = Regex("""([\w.\-]+):([\w.\-]+):([\w.\-+]+)""")

        /**
         * Leniently extracts coordinates from text pasted from a build script.
         * Supported formats:
         * - Plain coordinates: `com.example:my-plugin:1.0.0` (optionally with `@repositoryUrl`)
         * - Gradle Kotlin DSL: `implementation("com.example:my-plugin:1.0.0")`
         * - Gradle Groovy DSL: `implementation 'com.example:my-plugin:1.0.0'`
         * - Maven XML: a `<dependency>` block containing groupId/artifactId/version tags
         *
         * Returns null when no coordinates can be extracted.
         */
        fun parseLenient(input: String): MavenCoordinates? {
            val text = input.trim()
            if (text.isEmpty()) return null

            if (text.contains("<groupId>")) {
                return MavenCoordinates(
                    groupId = extractXmlTagValue(text, "groupId") ?: return null,
                    artifactId = extractXmlTagValue(text, "artifactId") ?: return null,
                    version = extractXmlTagValue(text, "version") ?: return null,
                )
            }

            val match = GAV_REGEX.find(text) ?: return null
            val (groupId, artifactId, version) = match.destructured
            val repositoryUrl = text.substringAfter('@', "").trim()
                .takeIf { it.startsWith("http") }
            return MavenCoordinates(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                repositoryUrl = repositoryUrl ?: MAVEN_CENTRAL_URL,
            )
        }

        private fun extractXmlTagValue(text: String, tag: String): String? = Regex("<$tag>\\s*([^<]+?)\\s*</$tag>").find(text)?.groupValues?.get(1)
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
