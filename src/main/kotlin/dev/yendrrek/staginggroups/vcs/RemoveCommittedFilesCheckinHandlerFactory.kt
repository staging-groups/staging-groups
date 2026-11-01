package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** Creates a [RemoveCommittedFilesCheckinHandler] for every commit, from the staging area too. */
class RemoveCommittedFilesCheckinHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
        RemoveCommittedFilesCheckinHandler(panel.project)
}

/**
 * With **Remove committed files from staging groups** on, takes each file a successful commit left
 * without changes out of its staging group. A file the commit took only part of, such as one with
 * unstaged changes too, keeps its group.
 *
 * The committed files are those the Commit tool window listed before the commit and no longer lists
 * after it, so files assigned ahead of any change stay put.
 */
class RemoveCommittedFilesCheckinHandler(private val project: Project) : CheckinHandler() {

    private var changedBeforeCommit: Set<String>? = null

    override fun beforeCheckin(): ReturnResult {
        changedBeforeCommit =
            if (StagingGroupsSettings.getInstance(project).snapshot().autoCleanup) ChangedFiles.loadedPaths(project) else null
        return ReturnResult.COMMIT
    }

    override fun checkinSuccessful() {
        val changedBefore = changedBeforeCommit ?: return
        // After the refresh the commit triggers: until then, the committed files still look changed.
        ChangeListManager.getInstance(project).invokeAfterUpdate(true) {
            if (project.isDisposed) return@invokeAfterUpdate
            val changedAfter = ChangedFiles.loadedPaths(project) ?: return@invokeAfterUpdate
            val committed = changedBefore - changedAfter
            if (committed.isNotEmpty()) StagingGroupsStore.getInstance(project).update { GroupsCommands.unassign(it, committed) }
        }
    }
}
