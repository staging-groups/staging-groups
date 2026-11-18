package dev.yendrrek.staginggroups.git

import com.intellij.openapi.project.Project
import dev.yendrrek.staginggroups.state.StagingGroupsListener
import git4idea.index.GitStageTracker

/** Rebuilds the Git staging area's tree when the groups change. */
class GitStageRefresher(private val project: Project) : StagingGroupsListener {

    override fun groupsChanged() {
        // The tracker rereads Git's in-memory status and notifies the staging panel, which rebuilds its tree.
        if (GitStagingAreaView.showsStagingArea(project)) GitStageTracker.getInstance(project).updateTrackerState()
    }
}
