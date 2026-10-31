package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.FileColorManager
import com.intellij.util.ui.ColorIcon
import dev.yendrrek.staginggroups.domain.StagingGroup
import javax.swing.Icon

object StagingGroupIcons {

    private const val COLOUR_DOT_SIZE = 12

    /** The group's colour as a dot, or `null` while [useColours] is off or the colour is unknown. */
    fun colourDot(project: Project, group: StagingGroup, useColours: Boolean): Icon? {
        if (!useColours) return null
        val colour = FileColorManager.getInstance(project).getColor(group.colourId) ?: return null
        return ColorIcon(COLOUR_DOT_SIZE, colour, true)
    }
}
