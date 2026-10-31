package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewManager
import dev.yendrrek.staginggroups.state.StagingGroupsListener

/** Rebuilds the Commit tool window's changelist tree when the groups or their display settings change. */
class ChangesViewRefresher(private val project: Project) : StagingGroupsListener {

    override fun groupsChanged() = refresh()

    override fun settingsChanged() = refresh()

    private fun refresh() {
        ChangesViewManager.getInstance(project).scheduleRefresh()
    }
}
