package dev.yendrrek.staginggroups.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.FileColorManager
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.ColorIcon
import dev.yendrrek.staginggroups.domain.StagingGroup
import java.awt.Color
import javax.swing.Icon

object StagingGroupIcons {

    private const val COLOUR_DOT_SIZE = 12
    private const val SQUARE_CORNERS = 0
    private const val OUTLINE_ALPHA = 40

    /**
     * The platform's colour-swatch outline, mirrored for dark themes: File Colors are muted tints
     * there, and without a light outline the darker ones vanish into the background.
     */
    private val outline = JBColor(Gray.x00.withAlpha(OUTLINE_ALPHA), Gray.xFF.withAlpha(OUTLINE_ALPHA))

    /** The group's colour as a dot, or `null` while [useColours] is off or the colour is unknown. */
    fun colourDot(project: Project, group: StagingGroup, useColours: Boolean): Icon? {
        if (!useColours) return null
        return colourDot(FileColorManager.getInstance(project).getColor(group.colourId))
    }

    /** [colour] as a dot, or `null` without a colour. */
    fun colourDot(colour: Color?): Icon? =
        colour?.let { ColorIcon(COLOUR_DOT_SIZE, COLOUR_DOT_SIZE, COLOUR_DOT_SIZE, COLOUR_DOT_SIZE, it, outline, SQUARE_CORNERS) }
}
