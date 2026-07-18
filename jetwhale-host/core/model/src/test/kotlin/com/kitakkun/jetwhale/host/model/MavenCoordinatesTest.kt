package com.kitakkun.jetwhale.host.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MavenCoordinatesTest {
    @Test
    fun parseLenient_plainCoordinates() {
        val coordinates = MavenCoordinates.parseLenient("com.example:my-plugin:1.0.0")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
        assertEquals(MavenCoordinates.MAVEN_CENTRAL_URL, coordinates?.repositoryUrl)
    }

    @Test
    fun parseLenient_plainCoordinatesWithRepositoryUrl() {
        val coordinates = MavenCoordinates.parseLenient("com.example:my-plugin:1.0.0@https://example.com/maven2")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
        assertEquals("https://example.com/maven2", coordinates?.repositoryUrl)
    }

    @Test
    fun parseLenient_gradleKotlinDsl() {
        val coordinates = MavenCoordinates.parseLenient("""implementation("com.example:my-plugin:1.0.0-alpha01")""")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0-alpha01", coordinates?.version)
    }

    @Test
    fun parseLenient_gradleGroovyDsl() {
        val coordinates = MavenCoordinates.parseLenient("implementation 'com.example:my-plugin:1.0.0'")
        assertEquals("com.example", coordinates?.groupId)
        assertEquals("my-plugin", coordinates?.artifactId)
        assertEquals("1.0.0", coordinates?.version)
    }

    @Test
    fun parseLenient_mavenXml() {
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
    fun parseLenient_mavenXmlMissingVersion() {
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
    fun parseLenient_unparseableInput() {
        assertNull(MavenCoordinates.parseLenient(""))
        assertNull(MavenCoordinates.parseLenient("   "))
        assertNull(MavenCoordinates.parseLenient("not coordinates"))
        assertNull(MavenCoordinates.parseLenient("group:artifact"))
    }

    @Test
    fun snapshotUrls() {
        val coordinates = MavenCoordinates("com.example", "my-plugin", "1.0.0-SNAPSHOT", "https://example.com/snapshots/")
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
    fun releaseIsNotSnapshot() {
        assertEquals(false, MavenCoordinates("com.example", "my-plugin", "1.0.0").isSnapshot)
    }
}
