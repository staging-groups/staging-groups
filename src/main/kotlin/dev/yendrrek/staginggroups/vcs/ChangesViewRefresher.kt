package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewManager
import dev.yendrrek.staginggroups.state.StagingGroupsListener

/** Rebuilds the Commit tool window's changelist tree when the groups change. */
class ChangesViewRefresher(private val project: Project) : StagingGroupsListener {

    override fun groupsChanged() {
        ChangesViewManager.getInstance(project).scheduleRefresh()
    }
}
