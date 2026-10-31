package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager

object ChangedFiles {

    /**
     * Paths of every file the Commit tool window lists: changed, deleted and unversioned files, from
     * whichever view it shows.
     */
    fun paths(project: Project): Set<String> =
        VcsChangesView.EP_NAME.extensionList.firstNotNullOfOrNull { it.changedPaths(project) } ?: changelistPaths(project)

    private fun changelistPaths(project: Project): Set<String> {
        val changeListManager = ChangeListManager.getInstance(project)
        return buildSet {
            for (change in changeListManager.allChanges) {
                (change.afterRevision ?: change.beforeRevision)?.file?.path?.let(::add)
            }
            for (path in changeListManager.unversionedFilesPaths) add(path.path)
        }
    }
}
