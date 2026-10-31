package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** Takes the selected files out of their staging groups. Enabled when any of them is in one. */
class RemoveFromStagingGroupAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isVisible = project != null
        if (project == null) return
        val snapshot = StagingGroupsStore.getInstance(project).snapshot()
        e.presentation.isEnabled = e.stagingGroupTargets().any { snapshot.groupFor(it) != null }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targets = e.stagingGroupTargets()
        StagingGroupsStore.getInstance(project).update { GroupsCommands.unassign(it, targets) }
    }
}
