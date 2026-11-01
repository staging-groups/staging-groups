package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsListener

/** Repaints file colours when the groups or the colour settings change. */
class FileColourRefresher(private val project: Project) : StagingGroupsListener {

    override fun groupsChanged() {
        if (StagingGroupsSettings.getInstance(project).snapshot().useColours) repaint()
    }

    override fun settingsChanged() = repaint()

    private fun repaint() {
        // Any modality: Settings applies its changes while its own modal dialog is still open.
        ApplicationManager.getApplication().invokeLater({ FileColourRepaint.request(project) }, ModalityState.any(), project.disposed)
    }
}
