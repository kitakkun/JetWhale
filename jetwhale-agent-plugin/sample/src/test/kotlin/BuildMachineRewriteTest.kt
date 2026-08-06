import sample.RecordingScope
import sample.declareEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the *published* plugin JAR transforms code compiled by this build's Kotlin.
 *
 * The point is the pairing, not the transform: CI runs this with the plugin built against the
 * shipped Kotlin and the sample compiled by each supported version in turn. A plugin that merely
 * recompiles against an older Kotlin proves nothing about the single artifact consumers download.
 */
class BuildMachineRewriteTest {
    @Test
    fun `buildMachineWss is rewritten to the address the build supplied`() {
        val scope = RecordingScope()
        declareEndpoints(scope)

        assertEquals(listOf("ws://localhost:5080", "wss://198.51.100.7:5443"), scope.dialled)
    }
}
