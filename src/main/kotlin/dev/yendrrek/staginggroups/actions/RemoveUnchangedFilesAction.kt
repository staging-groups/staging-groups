package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import dev.yendrrek.staginggroups.vcs.ChangedFiles

/**
 * Takes every file the Commit tool window no longer lists out of its staging group, such as a file
 * whose changes were committed or rolled back. Enabled when a group holds such a file.
 */
class RemoveUnchangedFilesAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project?.takeUnless { it.isDefault }
        e.presentation.isVisible = project != null
        if (project == null) return
        e.presentation.isEnabled = hasUnchangedFiles(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // After the pending refresh: a file changed a moment ago must not count as unchanged.
        ChangeListManager.getInstance(project).invokeAfterUpdate(true) {
            if (!project.isDisposed) removeUnchangedFiles(project)
        }
    }

    private fun hasUnchangedFiles(project: Project): Boolean {
        val assigned = StagingGroupsStore.getInstance(project).snapshot().state.assignments.keys
        if (assigned.isEmpty()) return false
        val changed = ChangedFiles.loadedPaths(project) ?: return false
        return assigned.any { it !in changed }
    }

    private fun removeUnchangedFiles(project: Project) {
        // Until the view has read every repository, any file could look unchanged.
        val changed = ChangedFiles.loadedPaths(project) ?: return
        StagingGroupsStore.getInstance(project).update { GroupsCommands.retain(it, changed) }
    }
}
