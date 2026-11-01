package dev.yendrrek.staginggroups.ui

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Makes the editor tabs and the Project view ask the colour providers again. Both cache a file's
 * colour until something asks for an update; the Commit and staging trees need no help, as they
 * rebuild on every staging group change.
 */
object FileColourRepaint {

    /** Call on the EDT. */
    fun request(project: Project) {
        if (project.isDisposed) return
        // Queues each tab's recomputation on the platform's own update channel. Unlike
        // updateFileColor, it refreshes the tab's background, not only its text.
        val editors = FileEditorManager.getInstance(project)
        editors.openFiles.forEach(editors::updateFilePresentation)
        ProjectView.getInstance(project).refresh()
    }
}
