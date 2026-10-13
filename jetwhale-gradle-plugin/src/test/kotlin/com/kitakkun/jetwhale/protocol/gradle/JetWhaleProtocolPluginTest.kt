package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JetWhaleProtocolPluginTest {
    private val projectDirectory: File = Files.createTempDirectory("jetwhale-protocol-plugin").toFile()
    private val committedWireSchemaFile: File get() = projectDirectory.resolve("protocol/wire/protocol.wire")
    private val messagesFile: File get() = projectDirectory.resolve("protocol/src/commonMain/kotlin/com/example/fixture/protocol/Messages.kt")

    @AfterTest
    fun deleteProjectDirectory() {
        projectDirectory.deleteRecursively()
    }

    @Test
    fun `a KMP module and a JVM-only module dump the same schema and the contract carries its hash`() {
        writeFixture(withJvmOnlyModule = true)

        runBuild("dumpWireSchema")

        assertEquals(EXPECTED_DUMP, committedWireSchemaFile.readText())
        assertEquals(EXPECTED_DUMP, projectDirectory.resolve("protocol-jvm/wire/protocol-jvm.wire").readText())

        val checked = runBuild("check", ":protocol:jvmJar")

        assertEquals(TaskOutcome.SUCCESS, checked.task(":protocol:checkWireSchema")?.outcome)
        val hash = WireSchema.parse(EXPECTED_DUMP).hash()
        val contractJson = ZipFile(projectDirectory.resolve("protocol/build/libs/protocol-jvm-1.4.0.jar")).use { jar ->
            jar.getInputStream(jar.getEntry("META-INF/jetwhale/protocols/com.example.fixture.json")).reader().readText()
        }
        assertEquals(
            """
            {
              "pluginId": "com.example.fixture",
              "releaseVersion": "1.4.0",
              "protocolRevision": 1,
              "wireSchemaHash": "$hash"
            }

            """.trimIndent(),
            contractJson,
        )
        val generatedContractSource = projectDirectory
            .resolve("protocol/build/generated/jetwhale/contract/kotlin/com/example/fixture/protocol/FixturePluginContract.kt")
            .readText()
        assertContains(generatedContractSource, "public val FixturePluginContract: JetWhalePluginContract = JetWhalePluginContract(")
        assertContains(generatedContractSource, "wireSchemaHash = \"$hash\",")

        runBuild("checkWireSchema")
        val repeated = runBuild("checkWireSchema")

        assertContains(repeated.output, "Configuration cache entry reused.")
    }

    @Test
    fun `a breaking change fails the check and the dump until the revision is raised`() {
        writeFixture(withJvmOnlyModule = false)
        committedWireSchemaFile.apply { parentFile.mkdirs() }.writeText(EXPECTED_DUMP)
        messagesFile.writeText(MESSAGES_SOURCE.replace("enum class Color { RED, @SerialName(\"green\") GREEN }", "enum class Color { RED, @SerialName(\"green\") GREEN, BLUE }"))

        val checkFailure = runFailingBuild("checkWireSchema")

        assertContains(checkFailure.output, "fixture/item_added.colorCounts{key}: enum entry BLUE added")
        assertContains(checkFailure.output, "Set protocolRevision to 2 and run dumpWireSchema.")

        val dumpFailure = runFailingBuild("dumpWireSchema")

        assertContains(dumpFailure.output, "Not writing protocol.wire. 1 breaking change(s) since the dump at protocol revision 1:")
        assertEquals(EXPECTED_DUMP, committedWireSchemaFile.readText())

        projectDirectory.resolve("protocol/build.gradle.kts").apply { writeText(readText().replace("protocolRevision.set(1)", "protocolRevision.set(2)")) }
        runBuild("dumpWireSchema", "checkWireSchema")

        assertTrue(committedWireSchemaFile.readText().startsWith("revision 2\n"))
        assertContains(committedWireSchemaFile.readText(), "  BLUE\n")
    }

    @Test
    fun `reordered fields keep the dump and an additive change is dumped at the same revision`() {
        writeFixture(withJvmOnlyModule = false)
        committedWireSchemaFile.apply { parentFile.mkdirs() }.writeText(EXPECTED_DUMP)
        messagesFile.writeText(MESSAGES_SOURCE.replace("    val id: ItemId,\n    val item: Item,\n", "    val item: Item,\n    val id: ItemId,\n"))

        runBuild("checkWireSchema")

        messagesFile.writeText(MESSAGES_SOURCE.replace("data class Pong(val atMillis: Long)", "data class Pong(val atMillis: Long, val label: String? = null)"))
        val checkFailure = runFailingBuild("checkWireSchema")

        assertContains(checkFailure.output, "additive  fixture/ping (reply).label: optional field added")
        assertContains(checkFailure.output, "Run dumpWireSchema and commit the result.")

        val dumped = runBuild("dumpWireSchema")

        assertContains(dumped.output, "additive  fixture/ping (reply).label: optional field added")
        assertTrue(committedWireSchemaFile.readText().startsWith("revision 1\n"))
        assertContains(committedWireSchemaFile.readText(), "  label: string? = …\n")
    }

    @Test
    fun `the baseline comparison refuses a dump edited by hand`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.writeText(MESSAGES_SOURCE.replace("enum class Color { RED, @SerialName(\"green\") GREEN }", "enum class Color { RED, @SerialName(\"green\") GREEN, BLUE }"))
        committedWireSchemaFile.apply { parentFile.mkdirs() }.writeText(EXPECTED_DUMP.replace("  RED\n", "  BLUE\n  RED\n"))
        val baselineDirectory = projectDirectory.resolve("baseline")
        baselineDirectory.resolve("protocol/wire/protocol.wire").apply { parentFile.mkdirs() }.writeText(EXPECTED_DUMP)

        runBuild("checkWireSchema")
        val failure = runFailingBuild("checkWireSchema", "-PjetwhaleWireSchemaBaselineDir=${baselineDirectory.absolutePath}")

        assertContains(failure.output, "protocol.wire cannot follow the baseline's dump")
        assertContains(failure.output, "fixture/item_added.colorCounts{key}: enum entry BLUE added")
    }

    @Test
    fun `an open polymorphic property is refused with its path`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            interface Animal

            @SerialName("fixture/animal_seen")
            @Serializable
            data class AnimalSeen(val animal: Animal) : JetWhaleEvent

            """.trimIndent(),
        )

        val failure = runFailingBuild("dumpWireSchema")

        assertContains(failure.output, "fixture/animal_seen.animal: ")
        assertContains(failure.output, "is open polymorphic. The plugin payload format has no serializers module, so it cannot be sent.")
    }

    @Test
    fun `a generic class reached with two type arguments is refused`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            @Serializable
            data class Labeled<T>(val value: T)

            @SerialName("fixture/labels_changed")
            @Serializable
            data class LabelsChanged(val count: Labeled<Int>, val name: Labeled<String>) : JetWhaleEvent

            """.trimIndent(),
        )

        val failure = runFailingBuild("dumpWireSchema")

        assertContains(failure.output, "fixture/labels_changed.name: com.example.fixture.protocol.Labeled is reached with two different shapes")
    }

    @Test
    fun `a reply type declared through a generic supertype is resolved`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            interface FixtureRequest<R : Any> : JetWhaleRequest<R>

            interface FixtureListRequest<R : Any> : FixtureRequest<List<R>>

            @SerialName("fixture/get_pong")
            @Serializable
            data object GetPong : FixtureRequest<Pong>

            @SerialName("fixture/get_pongs")
            @Serializable
            data object GetPongs : FixtureListRequest<Pong>

            """.trimIndent(),
        )

        runBuild("dumpWireSchema")

        assertContains(committedWireSchemaFile.readText(), "request fixture/get_pong -> fixture/pong\n")
        assertContains(committedWireSchemaFile.readText(), "request fixture/get_pongs -> list<fixture/pong>\n")
    }

    @Test
    fun `a field whose type is named like a built-in type is refused`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            @SerialName("string")
            @Serializable
            data class Label(val text: String)

            @SerialName("fixture/label_seen")
            @Serializable
            data class LabelSeen(val label: Label) : JetWhaleEvent

            """.trimIndent(),
        )

        val failure = runFailingBuild("dumpWireSchema")

        assertContains(failure.output, "fixture/label_seen.label: its type is named string, which is what the wire schema dump calls a built-in type.")
    }

    @Test
    fun `a message named like a built-in type is kept`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            @SerialName("list")
            @Serializable
            data object ListPongs : JetWhaleRequest<Pong>

            """.trimIndent(),
        )

        runBuild("dumpWireSchema")

        assertContains(committedWireSchemaFile.readText(), "request list -> fixture/pong\n")
        assertContains(committedWireSchemaFile.readText(), "\nobject list\n")
    }

    @Test
    fun `a message whose serializer describes no class is refused`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            @JvmInline
            @SerialName("fixture/count_changed")
            @Serializable
            value class CountChanged(val count: Int) : JetWhaleEvent

            """.trimIndent(),
        )

        val failure = runFailingBuild("dumpWireSchema")

        assertContains(failure.output, "com.example.fixture.protocol.CountChanged: a message is routed by its serial name")
    }

    @Test
    fun `a map keyed by a class is refused`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            @Serializable
            data class Point(val x: Int, val y: Int)

            @SerialName("fixture/points_seen")
            @Serializable
            data class PointsSeen(val labels: Map<Point, String>) : JetWhaleEvent

            """.trimIndent(),
        )

        val failure = runFailingBuild("dumpWireSchema")

        assertContains(failure.output, "fixture/points_seen.labels{key}: com.example.fixture.protocol.Point cannot key a JSON object.")
    }

    @Test
    fun `a name the dump would not read back is refused`() {
        writeFixture(withJvmOnlyModule = false)
        messagesFile.appendText(
            """

            @SerialName("fixture/tagged")
            @Serializable
            data class Tagged(@JsonNames("label, name") val tag: String = "") : JetWhaleEvent

            """.trimIndent(),
        )

        val failure = runFailingBuild("dumpWireSchema")

        assertContains(failure.output, "fixture/tagged: A name uses what the wire schema dump uses as syntax")
    }

    @Test
    fun `a contract name with a Kotlin keyword is refused`() {
        writeFixture(withJvmOnlyModule = false)
        projectDirectory.resolve("protocol/build.gradle.kts").apply {
            writeText(readText().replace("com.example.fixture.protocol.FixturePluginContract", "com.example.object.FixturePluginContract"))
        }

        val failure = runFailingBuild(":protocol:generateJetWhalePluginContract")

        assertContains(failure.output, "jetwhaleProtocol.contractQualifiedName must be a qualified Kotlin name")
    }

    @Test
    fun `a protocol revision below 1 is refused`() {
        writeFixture(withJvmOnlyModule = false)
        projectDirectory.resolve("protocol/build.gradle.kts").apply { writeText(readText().replace("protocolRevision.set(1)", "protocolRevision.set(0)")) }

        val failure = runFailingBuild(":protocol:generateJetWhalePluginContract")

        assertContains(failure.output, "jetwhaleProtocol.protocolRevision starts at 1, but it is 0.")
    }

    @Test
    fun `a module without a JVM target is refused`() {
        writeFixture(withJvmOnlyModule = false)
        projectDirectory.resolve("protocol/build.gradle.kts").apply { writeText(readText().replace("    jvm()\n", "")) }

        val failure = runFailingBuild(":protocol:help")

        assertContains(failure.output, "reads the wire schema from the classes of one JVM target, but :protocol has 0")
    }

    private fun runBuild(vararg arguments: String): BuildResult = gradleRunner(*arguments).build()

    private fun runFailingBuild(vararg arguments: String): BuildResult = gradleRunner(*arguments).buildAndFail()

    private fun gradleRunner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDirectory)
        .withPluginClasspath()
        .withArguments(*arguments, "--configuration-cache", "--stacktrace")

    private fun writeFixture(withJvmOnlyModule: Boolean) {
        val serializationVersion = System.getProperty("jetwhale.test.kotlinxSerializationVersion")
        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "fixture"

            include(":core", ":protocol")
            ${if (withJvmOnlyModule) "include(\":protocol-jvm\")" else ""}

            """.trimIndent(),
        )
        projectDirectory.resolve("gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2g
            kotlin.compiler.execution.strategy=in-process

            """.trimIndent(),
        )
        projectDirectory.resolve("core/build.gradle.kts").apply { parentFile.mkdirs() }.writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("org.jetbrains.kotlin.plugin.serialization")
            }

            kotlin {
                jvm()
                sourceSets.commonMain.dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
                }
            }

            """.trimIndent(),
        )
        projectDirectory.resolve("core/src/commonMain/kotlin/com/kitakkun/jetwhale/protocol/messaging/Markers.kt").apply { parentFile.mkdirs() }.writeText(MARKERS_SOURCE)
        projectDirectory.resolve("core/src/commonMain/kotlin/com/kitakkun/jetwhale/protocol/negotiation/JetWhalePluginContract.kt").apply { parentFile.mkdirs() }.writeText(CONTRACT_SOURCE)
        projectDirectory.resolve("core/src/commonMain/kotlin/com/kitakkun/jetwhale/protocol/fixture/Stamp.kt").apply { parentFile.mkdirs() }.writeText(STAMP_SOURCE)
        projectDirectory.resolve("protocol/build.gradle.kts").apply { parentFile.mkdirs() }.writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("org.jetbrains.kotlin.plugin.serialization")
                id("com.kitakkun.jetwhale.protocol")
            }

            version = "1.4.0"

            kotlin {
                jvm()
                sourceSets.commonMain.dependencies {
                    api(project(":core"))
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                }
            }

            jetwhaleProtocol {
                pluginId.set("com.example.fixture")
                protocolRevision.set(1)
                contractQualifiedName.set("com.example.fixture.protocol.FixturePluginContract")
            }

            """.trimIndent(),
        )
        messagesFile.apply { parentFile.mkdirs() }.writeText(MESSAGES_SOURCE)
        if (withJvmOnlyModule) {
            projectDirectory.resolve("protocol-jvm/build.gradle.kts").apply { parentFile.mkdirs() }.writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    id("org.jetbrains.kotlin.plugin.serialization")
                    id("com.kitakkun.jetwhale.protocol")
                }

                version = "1.4.0"

                kotlin {
                    sourceSets.main {
                        kotlin.srcDir("../protocol/src/commonMain/kotlin")
                    }
                }

                dependencies {
                    implementation(project(":core"))
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                }

                jetwhaleProtocol {
                    pluginId.set("com.example.fixture")
                    protocolRevision.set(1)
                    contractQualifiedName.set("com.example.fixture.protocol.FixturePluginContract")
                }

                """.trimIndent(),
            )
        }
    }

    private companion object {
        /** Stand-ins for what the fixture needs from jetwhale-protocol-core, under the same names. */
        val MARKERS_SOURCE = """
            package com.kitakkun.jetwhale.protocol.messaging

            interface JetWhaleEvent

            interface JetWhaleRequest<R : Any>

        """.trimIndent()

        /** A class the fixture's protocol reaches from a dependency, as a shared model library's would be. */
        val STAMP_SOURCE = """
            @file:OptIn(ExperimentalSerializationApi::class)

            package com.kitakkun.jetwhale.protocol.fixture

            import kotlinx.serialization.EncodeDefault
            import kotlinx.serialization.ExperimentalSerializationApi
            import kotlinx.serialization.Serializable

            @Serializable
            data class Stamp(
                val at: Long,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val zone: String = "UTC",
            )

        """.trimIndent()

        val CONTRACT_SOURCE = """
            package com.kitakkun.jetwhale.protocol.negotiation

            class JetWhalePluginContract(
                val pluginId: String,
                val releaseVersion: String,
                val protocolRevision: Int,
                val wireSchemaHash: String?,
            )

        """.trimIndent()

        val MESSAGES_SOURCE = """
            @file:OptIn(ExperimentalSerializationApi::class)

            package com.example.fixture.protocol

            import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
            import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
            import kotlinx.serialization.EncodeDefault
            import kotlinx.serialization.ExperimentalSerializationApi
            import kotlinx.serialization.SerialName
            import kotlinx.serialization.Serializable
            import kotlinx.serialization.json.JsonClassDiscriminator
            import kotlinx.serialization.json.JsonElement
            import kotlinx.serialization.json.JsonNames
            import com.kitakkun.jetwhale.protocol.fixture.Stamp
            import kotlin.jvm.JvmInline

            @SerialName("fixture/ping")
            @Serializable
            data object Ping : JetWhaleRequest<Pong>

            @SerialName("fixture/pong")
            @Serializable
            data class Pong(val atMillis: Long)

            @SerialName("fixture/item_added")
            @Serializable
            data class ItemAdded(
                val id: ItemId,
                val item: Item,
                @SerialName("tag_list") val tags: List<String> = emptyList(),
                @JsonNames("legacyCount") val count: Int = 0,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val note: String? = null,
                @SerialName("hidden_flag") @EncodeDefault(EncodeDefault.Mode.NEVER) val hidden: Boolean = false,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val isActive: Boolean = true,
                @EncodeDefault(EncodeDefault.Mode.NEVER) val owner: ItemId = ItemId("nobody"),
                val attributes: Map<String, Int> = emptyMap(),
                val caption: MaybeText = MaybeText(null),
                val stamp: Stamp? = null,
                val colorCounts: Map<Color, Int> = emptyMap(),
                val ownerNotes: Map<ItemId, String> = emptyMap(),
                val payload: JsonElement? = null,
            ) : JetWhaleEvent

            @SerialName("fixture/set_shape")
            @Serializable
            data class SetShape(val shape: Shape) : JetWhaleRequest<Ack>

            @SerialName("fixture/ack")
            @Serializable
            data object Ack

            @JvmInline
            @Serializable
            value class ItemId(val value: String)

            @JvmInline
            @Serializable
            value class MaybeText(val value: String?)

            @Serializable
            enum class Color { RED, @SerialName("green") GREEN }

            @Serializable
            sealed interface Item {
                @SerialName("box")
                @Serializable
                data class Box(val color: Color) : Item

                @SerialName("empty")
                @Serializable
                data object Empty : Item

                @SerialName("int")
                @Serializable
                data class Counted(val value: Int) : Item
            }

            @JsonClassDiscriminator("kind")
            @Serializable
            sealed class Shape {
                @SerialName("circle")
                @Serializable
                data class Circle(val radius: Double) : Shape()
            }

        """.trimIndent()

        val EXPECTED_DUMP = """
            revision 1

            event fixture/item_added
            request fixture/ping -> fixture/pong
            request fixture/set_shape -> fixture/ack

            class box
              color: com.example.fixture.protocol.Color

            class circle
              radius: double

            enum com.example.fixture.protocol.Color
              RED
              green

            sealed com.example.fixture.protocol.Item by type
              box
              empty
              int

            sealed com.example.fixture.protocol.Shape by kind
              circle

            class com.kitakkun.jetwhale.protocol.fixture.Stamp
              at: long
              zone: string = …, omitted at default

            object empty

            object fixture/ack

            class fixture/item_added
              attributes: map<string, int> = …
              caption: string? = …
              colorCounts: map<com.example.fixture.protocol.Color, int> = …
              count: int = … | also read as legacyCount
              hidden_flag: boolean = …, omitted at default
              id: string
              isActive: boolean = …, omitted at default
              item: com.example.fixture.protocol.Item
              note: string? = …, omitted at default
              owner: string = …, omitted at default
              ownerNotes: map<string, string> = …
              payload: json? = …
              stamp: com.kitakkun.jetwhale.protocol.fixture.Stamp? = …
              tag_list: list<string> = …

            object fixture/ping

            class fixture/pong
              atMillis: long

            class fixture/set_shape
              shape: com.example.fixture.protocol.Shape

            class int
              value: int

        """.trimIndent()
    }
}
