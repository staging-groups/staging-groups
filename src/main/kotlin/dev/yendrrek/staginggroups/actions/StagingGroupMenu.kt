package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

/**
 * The **Staging Group** submenu of the Commit view, staging area, Project view and editor tab context
 * menus. Shown only when the selection holds a file it can act on.
 */
class StagingGroupMenu : DefaultActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.stagingGroupTargets().isNotEmpty()
    }
}
