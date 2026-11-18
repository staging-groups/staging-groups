package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import dev.yendrrek.staginggroups.StagingGroupsBundle
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.settings.SettingsState
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import javax.swing.JLabel

/** **Settings | Version Control | Staging Groups**: clean-up after a commit, and every staging group. */
class StagingGroupsConfigurable(project: Project) :
    BoundSearchableConfigurable(StagingGroupsBundle.message("configurable.display.name"), "", ID) {

    private val settings = StagingGroupsSettings.getInstance(project)
    private val store = StagingGroupsStore.getInstance(project)

    internal val groupsTable = StagingGroupsTable()
    internal lateinit var removeCommittedFiles: JBCheckBox

    override fun createPanel(): DialogPanel = panel {
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

        onReset {
            removeCommittedFiles.isSelected = settings.snapshot().autoCleanup
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

    private fun editedSettings(): SettingsState = settings.snapshot().copy(autoCleanup = removeCommittedFiles.isSelected)

    companion object {
        const val ID = "dev.yendrrek.staginggroups.settings"

        private val NO_LABEL: JLabel? = null
    }
}
