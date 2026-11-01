package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Keeps files in their staging groups through renames made outside the IDE, such as `git mv`: when
 * the Commit tool window starts listing a file as renamed from a grouped path, the new path joins
 * that group, unless it is in a group already. The old path keeps its group.
 *
 * Only a rename the view starts listing counts, so a new path taken out of its group stays out for
 * as long as the view lists that rename. Renames listed when the project opens count as new.
 */
@Service(Service.Level.PROJECT)
class RenameFollower(private val project: Project, scope: CoroutineScope) : Disposable {

    // Conflated: a burst of updates is read once, after the last. One reader, so reads keep their order.
    private val updates = Channel<Unit>(Channel.CONFLATED)

    /** The renames listed at the last read; read and written by the reader only. */
    private var listed: Set<Rename> = emptySet()

    init {
        scope.launch {
            for (update in updates) follow()
        }
        VcsChangesView.EP_NAME.extensionList.forEach { it.addChangeListener(project, Runnable(::changesUpdated), this) }
    }

    /** Reads the Commit tool window's view again, off the calling thread. */
    fun changesUpdated() {
        updates.trySend(Unit)
    }

    private fun follow() {
        if (project.isDisposed) return
        val renames = ChangedFiles.loaded(project)?.renames ?: return
        val appeared = renames - listed
        listed = renames
        if (appeared.isEmpty()) return
        StagingGroupsStore.getInstance(project).update { state ->
            appeared.fold(state) { followed, rename -> GroupsCommands.followRename(followed, rename.oldPath, rename.newPath) }
        }
    }

    override fun dispose() = Unit

    /**
     * Reads the changelists again after each of their updates. Also creates the follower: a
     * VCS's own view, such as the Git staging area, takes in what it lists through such an update.
     */
    class ChangeListTrigger(private val project: Project) : ChangeListListener {
        override fun changeListUpdateDone() {
            if (!project.isDisposed) getInstance(project).changesUpdated()
        }
    }

    companion object {
        fun getInstance(project: Project): RenameFollower = project.service()
    }
}
