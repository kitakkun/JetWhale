package com.kitakkun.jetwhale.plugins.mirror.host

import kotlin.test.Test
import kotlin.test.assertEquals

class RunnerSigningTeamTest {
    private val signingTeam = RunnerSigningTeam()

    @Test
    fun `the first team read from storage is taken`() {
        signingTeam.adoptStoredTeam("ABCDE12345")

        assertEquals("ABCDE12345", signingTeam.developmentTeam)
    }

    @Test
    fun `a read that finishes after the user chose a team does not bring back the stored one`() {
        signingTeam.chooseTeam("VWXYZ67890")

        signingTeam.adoptStoredTeam("ABCDE12345")

        assertEquals("VWXYZ67890", signingTeam.developmentTeam)
    }

    @Test
    fun `another instance's later read does not replace the team already taken`() {
        signingTeam.adoptStoredTeam("ABCDE12345")

        signingTeam.adoptStoredTeam(null)

        assertEquals("ABCDE12345", signingTeam.developmentTeam)
    }
}
