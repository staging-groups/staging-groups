package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.settings.SettingsState
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** **Settings | Version Control | Staging Groups** editing the groups and the settings. */
class StagingGroupsConfigurableTest : BasePlatformTestCase() {

    private val store get() = StagingGroupsStore.getInstance(project)
    private val settings get() = StagingGroupsSettings.getInstance(project)

    private lateinit var configurable: StagingGroupsConfigurable

    override fun setUp() {
        super.setUp()
        // The light project is shared by every test class: start from default settings and two
        // groups with one file each.
        settings.update { SettingsState() }
        store.update {
            val withA = GroupsCommands.create(GroupsState(), "Feature A", "a")
            val withR = GroupsCommands.create(withA, "Refactor", "r")
            GroupsCommands.assign(GroupsCommands.assign(withR, listOf("/p/Foo.kt"), "a"), listOf("/p/Util.kt"), "r")
        }
        configurable = StagingGroupsConfigurable(project)
        configurable.createComponent()
        configurable.reset()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
            TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
            store.update { GroupsState() }
            settings.update { SettingsState() }
        } finally {
            super.tearDown()
        }
    }

    fun `test page shows the saved groups and settings, unmodified`() {
        assertEquals(store.snapshot().groups, configurable.groupsTable.groups())
        assertFalse(configurable.removeCommittedFiles.isSelected)
        assertFalse(configurable.isModified)
    }

    fun `test page offers no help topic`() {
        assertNull(configurable.helpTopic)
    }

    fun `test applying remove committed files saves it`() {
        configurable.removeCommittedFiles.isSelected = true
        assertTrue(configurable.isModified)

        configurable.apply()

        assertEquals(SettingsState(autoCleanup = true), settings.snapshot())
        assertFalse(configurable.isModified)
    }

    fun `test adding a group saves it on Apply`() {
        answerInputDialogWith("Docs")

        configurable.groupsTable.addGroup()
        assertEquals(2, store.snapshot().groups.size)
        configurable.apply()

        assertEquals(listOf("Feature A", "Refactor", "Docs"), store.snapshot().groups.map { it.name })
    }

    fun `test adding refuses a name already in the table, ignoring case`() {
        answerInputDialogWith("refactor")

        configurable.groupsTable.addGroup()

        assertEquals(listOf("Feature A", "Refactor"), configurable.groupsTable.groups().map { it.name })
    }

    fun `test renaming keeps the group's files`() {
        configurable.groupsTable.table.setRowSelectionInterval(1, 1)
        answerInputDialogWith(" Cleanup ")

        configurable.groupsTable.renameSelectedGroup()
        configurable.apply()

        assertEquals(listOf("Feature A", "Cleanup"), store.snapshot().groups.map { it.name })
        assertEquals(setOf("/p/Util.kt"), store.snapshot().pathsIn("r"))
    }

    fun `test reordering and deleting apply together, ungrouping the deleted group's files`() {
        val model = configurable.groupsTable.table.listTableModel
        model.exchangeRows(0, 1)
        assertTrue(configurable.isModified)
        model.removeRow(1)

        configurable.apply()

        assertEquals(listOf("r"), store.snapshot().groups.map { it.id })
        assertEquals(mapOf("/p/Util.kt" to "r"), store.snapshot().state.assignments)
    }

    fun `test reset discards edits`() {
        configurable.groupsTable.table.listTableModel.removeRow(0)
        configurable.removeCommittedFiles.isSelected = true

        configurable.reset()

        assertEquals(store.snapshot().groups, configurable.groupsTable.groups())
        assertFalse(configurable.removeCommittedFiles.isSelected)
        assertFalse(configurable.isModified)
    }

    fun `test page is registered under Version Control`() {
        val pages = Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
        val registered = pages.single { it.id == StagingGroupsConfigurable.ID }

        // A parent ID no page has puts the page at the top level instead.
        assertEquals("project.propVCSSupport.Mappings", registered.parentId)
        assertNotNull(pages.singleOrNull { it.id == registered.parentId })
        assertEquals(StagingGroupsConfigurable::class.java.name, registered.instanceClass)
    }

    private fun answerInputDialogWith(name: String) {
        TestDialogManager.setTestInputDialog(
            object : TestInputDialog {
                override fun show(message: String): String? = show(message, null)

                override fun show(message: String, validator: InputValidator?): String? =
                    name.takeIf { validator == null || validator.canClose(it) }
            },
        )
    }
}
