package com.kitakkun.jetwhale.agent.compiler.test

import org.junit.jupiter.api.Test

/**
 * Named per fixture rather than generated, because there are three of them.
 *
 * The framework ships a `generateTestGroupSuiteWithJUnit5` generator that walks `testData/` and
 * emits a class per runner; that pays off at dozens of fixtures and costs a regeneration step you
 * can forget. At this size, listing them is the cheaper mistake to avoid.
 */
class BuildMachineBoxTest : AbstractBuildMachineBoxTest() {
    @Test
    fun `buildMachineWss becomes wss at the supplied address`() {
        runTest("testData/box/rewrite.kt")
    }

    @Test
    fun `a same-named member on another interface is left alone`() {
        runTest("testData/box/notOurScope.kt")
    }

    @Test
    fun `every occurrence is rewritten, including nested ones`() {
        runTest("testData/box/nestedAndRepeated.kt")
    }
}
