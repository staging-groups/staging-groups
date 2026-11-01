package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.FileColorManager
import dev.yendrrek.staginggroups.lifecycle.Unloading
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import java.awt.Color

/**
 * Tints a grouped file with its staging group's colour: its editor tab, and its rows in the Project
 * view and the Commit and staging trees, each only while its setting is on. Ungrouped files get
 * `null`, so the IDE's File Colors scopes still apply to them.
 *
 * Called on every tab and row paint, on and off the EDT: reads snapshots only. `DumbAware`, as the
 * platform skips other providers while indexing.
 */
class StagingGroupColorProvider : EditorTabColorProvider, DumbAware {

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? =
        colourFor(project, file) { it.colourEditorTabs }

    override fun getProjectViewColor(project: Project, file: VirtualFile): Color? =
        colourFor(project, file) { it.colourFileTrees }

    private fun colourFor(project: Project, file: VirtualFile, surfaceOn: (StagingGroupsSettings) -> Boolean): Color? {
        if (Unloading.started || project.isDisposed) return null
        if (!surfaceOn(StagingGroupsSettings.getInstance(project))) return null
        val group = StagingGroupsStore.getInstance(project).snapshot().groupFor(file.path) ?: return null
        return FileColorManager.getInstance(project).getColor(group.colourId)
    }
}
