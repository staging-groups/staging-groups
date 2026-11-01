package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import dev.yendrrek.staginggroups.StagingGroupsBundle
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.settings.SettingsState
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import javax.swing.JLabel

/**
 * **Settings | Version Control | Staging Groups**: the colour settings, clean-up after a commit, and
 * every staging group.
 */
class StagingGroupsConfigurable(project: Project) :
    BoundSearchableConfigurable(StagingGroupsBundle.message("configurable.display.name"), "", ID) {

    private val settings = StagingGroupsSettings.getInstance(project)
    private val store = StagingGroupsStore.getInstance(project)

    internal val groupsTable = StagingGroupsTable(project)
    internal lateinit var useColours: JBCheckBox
    internal lateinit var colourTabs: JBCheckBox
    internal lateinit var colourTrees: JBCheckBox
    internal lateinit var removeCommittedFiles: JBCheckBox

    override fun createPanel(): DialogPanel = panel {
        row(NO_LABEL) {
            useColours = checkBox(StagingGroupsBundle.message("settings.use.colours")).component
        }
        indent {
            row(NO_LABEL) {
                colourTabs = checkBox(StagingGroupsBundle.message("settings.colour.tabs")).component
            }
            row(NO_LABEL) {
                colourTrees = checkBox(StagingGroupsBundle.message("settings.colour.trees")).component
            }
        }.enabledIf(useColours.selected)
        row(NO_LABEL) {
            removeCommittedFiles = checkBox(StagingGroupsBundle.message("settings.remove.committed.files"))
                .comment(StagingGroupsBundle.message("settings.remove.committed.files.comment"))
                .component
        }
        group(StagingGroupsBundle.message("settings.groups.title"), true) {
            row(NO_LABEL) {
                cell(groupsTable.component).align(Align.FILL)
            }.resizableRow()
        }.resizableRow()

        // The colour column follows the switch as it is ticked, before Apply.
        useColours.selected.addListener(groupsTable::showColours)

        onReset {
            val current = settings.snapshot()
            useColours.isSelected = current.useColours
            colourTabs.isSelected = current.colourTabs
            colourTrees.isSelected = current.colourTrees
            removeCommittedFiles.isSelected = current.autoCleanup
            groupsTable.showColours(current.useColours)
            groupsTable.reset(store.snapshot().groups)
        }
        onIsModified { editedSettings() != settings.snapshot() || groupsTable.groups() != store.snapshot().groups }
        onApply {
            val groups = groupsTable.groups()
            store.update { GroupsCommands.replaceGroups(it, groups) }
            val edited = editedSettings()
            settings.update { edited }
        }
    }

    /** No help page exists for it. */
    override fun getHelpTopic(): String? = null

    private fun editedSettings(): SettingsState = settings.snapshot().copy(
        useColours = useColours.isSelected,
        colourTabs = colourTabs.isSelected,
        colourTrees = colourTrees.isSelected,
        autoCleanup = removeCommittedFiles.isSelected,
    )

    companion object {
        const val ID = "dev.yendrrek.staginggroups.settings"

        private val NO_LABEL: JLabel? = null
    }
}
