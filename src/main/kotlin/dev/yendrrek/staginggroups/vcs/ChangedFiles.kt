package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager

object ChangedFiles {

    /**
     * Paths of every file the Commit tool window lists: changed, deleted and unversioned files, from
     * whichever view it shows.
     */
    fun paths(project: Project): Set<String> = shownPaths(project).paths

    /**
     * Like [paths], or `null` while the view the Commit tool window shows is still reading its
     * repositories for the first time. The changelists count as read: wait for their update with
     * [ChangeListManager.invokeAfterUpdate].
     */
    fun loadedPaths(project: Project): Set<String>? = shownPaths(project).takeIf { it.isLoaded }?.paths

    private fun shownPaths(project: Project): ChangedPaths =
        VcsChangesView.EP_NAME.extensionList.firstNotNullOfOrNull { it.changedPaths(project) }
            ?: ChangedPaths(changelistPaths(project), isLoaded = true)

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
