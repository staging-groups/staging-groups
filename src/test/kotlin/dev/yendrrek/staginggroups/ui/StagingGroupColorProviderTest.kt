package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.FileColorManager
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.settings.SettingsState
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** Grouped files tinted with their staging group's colour, on each surface its setting allows. */
class StagingGroupColorProviderTest : BasePlatformTestCase() {

    private val provider = StagingGroupColorProvider()
    private val settings get() = StagingGroupsSettings.getInstance(project)

    override fun setUp() {
        super.setUp()
        // The light project is shared by every test class: start from default settings.
        settings.update { SettingsState() }
        val grouped = myFixture.addFileToProject("src/Grouped.kt", "").virtualFile
        StagingGroupsStore.getInstance(project).update {
            GroupsCommands.assign(GroupsCommands.create(GroupsState(), "Feature A", "Violet", "a"), listOf(grouped.path), "a")
        }
    }

    override fun tearDown() {
        try {
            StagingGroupsStore.getInstance(project).update { GroupsState() }
            settings.update { SettingsState() }
        } finally {
            super.tearDown()
        }
    }

    fun `test colours nothing while colours are off`() {
        val grouped = myFixture.findFileInTempDir("src/Grouped.kt")

        assertNull(provider.getEditorTabColor(project, grouped))
        assertNull(provider.getProjectViewColor(project, grouped))
    }

    fun `test tints a grouped file on every surface with colours on, and no ungrouped file`() {
        settings.update { it.copy(useColours = true) }
        val violet = FileColorManager.getInstance(project).getColor("Violet")
        val ungrouped = myFixture.addFileToProject("src/Ungrouped.kt", "").virtualFile

        assertEquals(violet, provider.getEditorTabColor(project, myFixture.findFileInTempDir("src/Grouped.kt")))
        assertEquals(violet, provider.getProjectViewColor(project, myFixture.findFileInTempDir("src/Grouped.kt")))
        assertNull(provider.getEditorTabColor(project, ungrouped))
        assertNull(provider.getProjectViewColor(project, ungrouped))
    }

    fun `test each surface follows its own setting`() {
        val grouped = myFixture.findFileInTempDir("src/Grouped.kt")

        settings.update { it.copy(useColours = true, colourTabs = false) }
        assertNull(provider.getEditorTabColor(project, grouped))
        assertNotNull(provider.getProjectViewColor(project, grouped))

        settings.update { it.copy(colourTabs = true, colourTrees = false) }
        assertNotNull(provider.getEditorTabColor(project, grouped))
        assertNull(provider.getProjectViewColor(project, grouped))
    }

    fun `test runs before the IDE's own File Colors provider`() {
        assertInstanceOf(EditorTabColorProvider.EP_NAME.extensionList.first(), StagingGroupColorProvider::class.java)
    }
}
