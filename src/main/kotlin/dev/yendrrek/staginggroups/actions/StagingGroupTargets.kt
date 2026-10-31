package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import dev.yendrrek.staginggroups.domain.AssignTargets
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.vcs.ChangedFiles
import dev.yendrrek.staginggroups.vcs.VcsChangesView

/**
 * The files the Staging Group actions act on: the selected files, with each selected folder replaced
 * by the changed files under it. Empty without a project or a selection.
 */
internal fun AnActionEvent.stagingGroupTargets(): List<String> {
    val project = project ?: return emptyList()
    val selection = selectedPaths()
    if (selection.isEmpty()) return emptyList()
    return AssignTargets.resolve(selection) { ChangedFiles.paths(project) }
}

/**
 * Changes trees list deleted files, which have no virtual file, so their selection is read from
 * their own data. Everywhere else, such as the Project view and editor tabs, it is read from the
 * selected virtual files.
 */
private fun AnActionEvent.selectedPaths(): List<SelectedPath> {
    VcsChangesView.EP_NAME.extensionList.firstNotNullOfOrNull { it.selectedPaths(dataContext) }?.let { return it }
    if (getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) is ChangesListView) return changelistSelection()
    val files = getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: getData(CommonDataKeys.VIRTUAL_FILE)?.let { arrayOf(it) } ?: return emptyList()
    return files.filter { it.isInLocalFileSystem }.map { SelectedPath(it.path, it.isDirectory) }
}

/** The changelist tree's changes and unversioned files in or under the selected nodes. */
private fun AnActionEvent.changelistSelection(): List<SelectedPath> {
    val changes = getData(VcsDataKeys.CHANGES).orEmpty().mapNotNull { (it.afterRevision ?: it.beforeRevision)?.file }
    val unversioned = getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY) ?: emptyList()
    return (changes + unversioned).map { SelectedPath(it.path, it.isDirectory) }
}
