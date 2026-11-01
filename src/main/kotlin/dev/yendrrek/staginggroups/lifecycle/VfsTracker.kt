package dev.yendrrek.staginggroups.lifecycle

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/**
 * Keeps files in their staging groups when they are renamed or moved, files under a directory
 * included. A deleted file keeps its group as it is: the VCS still lists it as a change.
 *
 * Follows what the IDE renames or moves. A rename outside the IDE reaches it as a deletion and a new
 * file; [dev.yendrrek.staginggroups.vcs.RenameFollower] follows it once the VCS reports it as a
 * rename, as Git does for `git mv`.
 */
class VfsTracker(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        // The common case first, without touching the store: no rename or move in the batch.
        val moves = events.mapNotNull { it.moveOrNull() }
        if (moves.isEmpty() || project.isDisposed) return
        StagingGroupsStore.getInstance(project).update { state ->
            moves.fold(state) { moved, (oldPath, newPath) -> GroupsCommands.followMove(moved, oldPath, newPath) }
        }
    }

    /** The old and new path of a rename or move on disk; `null` for any other event. */
    private fun VFileEvent.moveOrNull(): Pair<String, String>? {
        // Assignments hold local paths only, never one inside an archive.
        if (fileSystem !is LocalFileSystem) return null
        // Both paths are read from the parents as they are now. The IDE renames and moves one file
        // per batch, so they are the paths of this event.
        return when (this) {
            is VFileMoveEvent -> oldPath to newPath
            is VFilePropertyChangeEvent -> if (isRename) oldPath to newPath else null
            else -> null
        }
    }
}
