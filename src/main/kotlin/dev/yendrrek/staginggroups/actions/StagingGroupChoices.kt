package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAware
import dev.yendrrek.staginggroups.StagingGroupsBundle
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/**
 * One [AssignToStagingGroupAction] per staging group, in display order, or [NoStagingGroupsAction]
 * while there are none.
 */
class StagingGroupChoices : ActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return EMPTY_ARRAY
        val groups = StagingGroupsStore.getInstance(project).snapshot().groups
        if (groups.isEmpty()) return arrayOf(NoStagingGroupsAction())
        return groups.map { AssignToStagingGroupAction(it.id) }.toTypedArray()
    }
}

/** A disabled entry saying there are no staging groups yet, in place of the list of groups. */
class NoStagingGroupsAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = StagingGroupsBundle.message("menu.no.groups")
        e.presentation.isVisible = true
        e.presentation.isEnabled = false
    }

    override fun actionPerformed(e: AnActionEvent) {}
}

/**
 * Assigns the selected files to the staging group with [groupId]. Ticked when every selected file is
 * already in it.
 */
class AssignToStagingGroupAction(private val groupId: String) : AnAction(), Toggleable, DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val snapshot = e.project?.let { StagingGroupsStore.getInstance(it).snapshot() }
        val group = snapshot?.group(groupId)
        if (snapshot == null || group == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val targets = e.stagingGroupTargets()
        // Group names are free text: without `false`, an underscore would be taken as a mnemonic.
        e.presentation.setText(group.name, false)
        e.presentation.isEnabledAndVisible = targets.isNotEmpty()
        Toggleable.setSelected(e.presentation, targets.isNotEmpty() && targets.all { snapshot.groupFor(it)?.id == groupId })
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targets = e.stagingGroupTargets()
        if (targets.isEmpty()) return
        StagingGroupsStore.getInstance(project).update { state ->
            // The group may have been deleted since the menu opened.
            if (state.groups.none { it.id == groupId }) state else GroupsCommands.assign(state, targets, groupId)
        }
    }
}
