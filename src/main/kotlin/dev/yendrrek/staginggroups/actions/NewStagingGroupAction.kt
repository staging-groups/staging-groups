package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import dev.yendrrek.staginggroups.StagingGroupsBundle
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import dev.yendrrek.staginggroups.ui.StagingGroupPalette
import java.util.UUID

/** Asks for a name, then creates a staging group holding the selected files, if any. */
class NewStagingGroupAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // Read before the dialog: the selection's data context does not outlive it.
        val targets = e.stagingGroupTargets()
        val store = StagingGroupsStore.getInstance(project)
        val name = Messages.showInputDialog(
            project,
            StagingGroupsBundle.message("dialog.group.name"),
            StagingGroupsBundle.message("dialog.new.group.title"),
            null,
            "",
            NameValidator { store.state },
        ) ?: return
        val palette = StagingGroupPalette.colourIds(project)
        store.update { state ->
            // Another change may have taken the name while the dialog was open.
            if (GroupsCommands.nameProblem(state, name) != null) return@update state
            val id = UUID.randomUUID().toString()
            val created = GroupsCommands.create(state, name, GroupsCommands.nextColourId(state, palette), id)
            GroupsCommands.assign(created, targets, id)
        }
    }
}

/**
 * Accepts a name no other staging group has. A blank name only disables OK: an error shown before the
 * user has typed anything would read as a mistake.
 */
internal class NameValidator(private val exceptId: String? = null, private val state: () -> GroupsState) : InputValidatorEx {

    override fun getErrorText(inputString: String): String? =
        // Named as the existing group is spelt, not as typed: the comparison ignores case.
        GroupsCommands.namesake(state(), inputString, exceptId)?.let { StagingGroupsBundle.message("validation.name.duplicate", it.name) }

    override fun checkInput(inputString: String): Boolean = GroupsCommands.nameProblem(state(), inputString, exceptId) == null

    override fun canClose(inputString: String): Boolean = checkInput(inputString)
}
