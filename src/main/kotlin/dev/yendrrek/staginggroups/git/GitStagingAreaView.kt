package dev.yendrrek.staginggroups.git

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.vcs.commit.CommitModeManager
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.vcs.VcsChangesView
import git4idea.index.GitStageTracker
import git4idea.index.ui.GitStageDataKeys

/** The Git staging area: staged, unstaged, untracked and conflicted files. */
class GitStagingAreaView : VcsChangesView {

    override fun changedPaths(project: Project): Set<String>? {
        if (!showsStagingArea(project)) return null
        return GitStageTracker.getInstance(project).state.rootStates.values.flatMapTo(HashSet()) { root ->
            root.statuses.values.filterNot { it.isIgnored() }.map { it.path.path }
        }
    }

    override fun selectedPaths(dataContext: DataContext): List<SelectedPath>? =
        dataContext.getData(GitStageDataKeys.GIT_FILE_STATUS_NODES)?.map { SelectedPath(it.filePath.path, it.filePath.isDirectory) }

    companion object {
        /**
         * Whether the Commit tool window shows the staging area. Git is the only VCS with a view in
         * place of the changelists, so a hidden changelist view means the staging area.
         */
        fun showsStagingArea(project: Project): Boolean =
            CommitModeManager.getInstance(project).getCurrentCommitMode().isLocalChangesTabHidden
    }
}
