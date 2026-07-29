package com.kitakkun.jetwhale.host.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsScreenMenuTest {

    @Test
    fun `every section has at least one page`() {
        // firstPage resolves with first {}, so a section nobody gave a page to would not fail here —
        // it would throw when the settings screen tried to open it.
        val sectionsWithoutPages = SettingsScreenSection.entries
            .filter { section -> SettingsScreenPage.entries.none { it.section == section } }

        assertTrue(sectionsWithoutPages.isEmpty(), "Sections with no page: $sectionsWithoutPages")
    }

    @Test
    fun `a section starts at its first declared page`() {
        SettingsScreenSection.entries.forEach { section ->
            assertEquals(
                SettingsScreenPage.entries.first { it.section == section },
                section.firstPage,
                "Wrong entry page for $section",
            )
        }
    }

    @Test
    fun `pages of a section are declared together`() {
        // The menu renders pages by filtering the enum per section, so interleaving two sections'
        // pages would silently reorder the list away from the declaration order it reads as.
        val sectionRuns = SettingsScreenPage.entries
            .map { it.section }
            .fold(mutableListOf<SettingsScreenSection>()) { runs, section ->
                if (runs.lastOrNull() != section) runs.add(section)
                runs
            }

        assertEquals(sectionRuns.distinct(), sectionRuns, "A section's pages are split apart: $sectionRuns")
    }
}
