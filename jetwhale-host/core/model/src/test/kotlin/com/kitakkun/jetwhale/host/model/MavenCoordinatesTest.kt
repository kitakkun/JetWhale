package com.kitakkun.jetwhale.host.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MavenCoordinatesTest {
    @Test
    fun `parses plain coordinates and defaults to Maven Central`() {
        val coordinates = MavenCoordinates.parseLenient("com.example:my-plugin:1.0.0")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
        assertEquals(MavenCoordinates.MAVEN_CENTRAL_URL, coordinates?.repositoryUrl)
    }

    @Test
    fun `parses plain coordinates with a repository url`() {
        val coordinates = MavenCoordinates.parseLenient("com.example:my-plugin:1.0.0@https://example.com/maven2")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
        assertEquals("https://example.com/maven2", coordinates?.repositoryUrl)
    }

    @Test
    fun `parses a Gradle Kotlin DSL dependency line`() {
        val coordinates = MavenCoordinates.parseLenient("""implementation("com.example:my-plugin:1.0.0-alpha01")""")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0-alpha01", coordinates?.version)
    }

    @Test
    fun `parses a Gradle Groovy DSL dependency line`() {
        val coordinates = MavenCoordinates.parseLenient("implementation 'com.example:my-plugin:1.0.0'")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
    }

    @Test
    fun `parses a Maven XML dependency block`() {
        val coordinates = MavenCoordinates.parseLenient(
            """
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>my-plugin</artifactId>
                <version>1.0.0</version>
            </dependency>
            """.trimIndent(),
        )
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
    }

    @Test
    fun `returns null for a Maven XML block without a version`() {
        val coordinates = MavenCoordinates.parseLenient(
            """
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>my-plugin</artifactId>
            </dependency>
            """.trimIndent(),
        )
        assertNull(coordinates)
    }

    @Test
    fun `returns null for input that holds no coordinates`() {
        assertNull(MavenCoordinates.parseLenient(""))
        assertNull(MavenCoordinates.parseLenient("   "))
        assertNull(MavenCoordinates.parseLenient("not coordinates"))
        assertNull(MavenCoordinates.parseLenient("group:artifact"))
    }

    @Test
    fun `builds directory and jar urls for a snapshot`() {
        val coordinates = MavenCoordinates(
            groupId = "com.example",
            artifactId = "my-plugin",
            version = "1.0.0-SNAPSHOT",
            repositoryUrl = "https://example.com/snapshots/",
        )
        assertEquals(true, coordinates.isSnapshot)
        assertEquals(
            "https://example.com/snapshots/com/example/my-plugin/1.0.0-SNAPSHOT",
            coordinates.toVersionDirectoryUrl(),
        )
        assertEquals(
            "https://example.com/snapshots/com/example/my-plugin/1.0.0-SNAPSHOT/my-plugin-1.0.0-20260718.103017-1.jar",
            coordinates.toSnapshotJarUrl("1.0.0-20260718.103017-1"),
        )
    }

    @Test
    fun `reports a release version as not a snapshot`() {
        assertEquals(false, MavenCoordinates(groupId = "com.example", artifactId = "my-plugin", version = "1.0.0").isSnapshot)
    }
}
