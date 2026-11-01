package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager

object ChangedFiles {

    /**
     * Paths of every file the Commit tool window lists: changed, deleted and unversioned files, from
     * whichever view it shows.
     */
    fun paths(project: Project): Set<String> = shown(project).paths

    /** Like [paths], or `null` while [loaded] is. */
    fun loadedPaths(project: Project): Set<String>? = loaded(project)?.paths

    /**
     * What the view the Commit tool window shows lists, or `null` while that view is still reading
     * its repositories for the first time. The changelists count as read: wait for their update
     * with [ChangeListManager.invokeAfterUpdate].
     */
    fun loaded(project: Project): ChangedPaths? = shown(project).takeIf { it.isLoaded }

    private fun shown(project: Project): ChangedPaths =
        VcsChangesView.EP_NAME.extensionList.firstNotNullOfOrNull { it.changedPaths(project) } ?: changelists(project)

    private fun changelists(project: Project): ChangedPaths {
        val changeListManager = ChangeListManager.getInstance(project)
        val paths = HashSet<String>()
        val renames = HashSet<Rename>()
        for (change in changeListManager.allChanges) {
            val before = change.beforeRevision?.file?.path
            val after = change.afterRevision?.file?.path
            (after ?: before)?.let(paths::add)
            if (before != null && after != null && before != after) renames += Rename(before, after)
        }
        changeListManager.unversionedFilesPaths.mapTo(paths) { it.path }
        return ChangedPaths(paths, renames, isLoaded = true)
    }
}
