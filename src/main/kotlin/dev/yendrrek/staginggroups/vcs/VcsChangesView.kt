package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import dev.yendrrek.staginggroups.domain.SelectedPath

/**
 * A VCS's own view of changed files that the Commit tool window can show in place of the
 * changelists, such as the Git staging area. Lets the plugin read that view without depending on
 * its VCS.
 */
interface VcsChangesView {

    /** The files the view lists, or `null` while the Commit tool window shows another view. */
    fun changedPaths(project: Project): ChangedPaths?

    /**
     * The files and folders selected in the view's tree, including those under selected nodes, or
     * `null` when [dataContext] does not come from that tree.
     */
    fun selectedPaths(dataContext: DataContext): List<SelectedPath>?

    companion object {
        val EP_NAME: ExtensionPointName<VcsChangesView> = ExtensionPointName("dev.yendrrek.staginggroups.vcsChangesView")
    }
}

/**
 * The [paths] of every file a [VcsChangesView] lists. [isLoaded] is `false` until the view has read
 * every repository once; until then, [paths] can miss changed files.
 */
data class ChangedPaths(val paths: Set<String>, val isLoaded: Boolean)
