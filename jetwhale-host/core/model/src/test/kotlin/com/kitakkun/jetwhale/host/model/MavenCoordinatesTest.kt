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
}
