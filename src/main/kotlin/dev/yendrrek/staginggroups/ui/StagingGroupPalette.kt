package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.FileColorManager

/** The File Colors a staging group can take, in the order new groups take them. */
object StagingGroupPalette {

    /** The neutral grey goes last: it barely stands apart from an uncoloured row. */
    private const val GREY_ID = "Gray"

    fun colourIds(project: Project): List<String> =
        FileColorManager.getInstance(project).colorIDs.sortedBy { it == GREY_ID }
}
