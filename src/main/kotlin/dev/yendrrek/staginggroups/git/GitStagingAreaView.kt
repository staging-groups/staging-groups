package dev.yendrrek.staginggroups.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.vcs.commit.CommitModeManager
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.vcs.ChangedPaths
import dev.yendrrek.staginggroups.vcs.Rename
import dev.yendrrek.staginggroups.vcs.VcsChangesView
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.index.ui.GitStageDataKeys

/** The Git staging area: staged, unstaged, untracked and conflicted files. */
class GitStagingAreaView : VcsChangesView {

    override fun changedPaths(project: Project): ChangedPaths? {
        if (!showsStagingArea(project)) return null
        // One read of the immutable state, so the paths and whether they are complete agree.
        val roots = GitStageTracker.getInstance(project).state.rootStates.values
        val statuses = roots.flatMap { root -> root.statuses.values.filterNot { it.isIgnored() } }
        val paths = statuses.mapTo(HashSet()) { it.path.path }
        // A staged rename, such as one made with `git mv`, is one status at the new path.
        val renames = statuses.mapNotNullTo(HashSet()) { status ->
            status.origPath?.path?.takeIf { it != status.path.path }?.let { Rename(it, status.path.path) }
        }
        return ChangedPaths(paths, renames, isLoaded = roots.all { it.initialized })
    }

    override fun addChangeListener(project: Project, listener: Runnable, parentDisposable: Disposable) {
        val stageListener = object : GitStageTrackerListener {
            override fun update() = listener.run()
        }
        GitStageTracker.getInstance(project).addListener(stageListener, parentDisposable)
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
