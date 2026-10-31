package dev.yendrrek.staginggroups.vcs

import com.intellij.icons.AllIcons
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.FileColorManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.ColorIcon
import dev.yendrrek.staginggroups.domain.StagingGroup
import javax.swing.Icon

/**
 * A staging group in a changes tree: a folder icon, or the group's colour dot while [useColours] is
 * on, then the name in bold and the file count.
 */
class StagingGroupNode(val group: StagingGroup, private val useColours: Boolean) : ChangesBrowserNode<StagingGroup>(group) {

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
        renderer.icon = icon(renderer)
        renderer.append(group.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        appendCount(renderer)
    }

    private fun icon(renderer: ChangesBrowserNodeRenderer): Icon {
        val project = renderer.project
        if (!useColours || project == null) return AllIcons.Nodes.Folder
        val colour = FileColorManager.getInstance(project).getColor(group.colourId) ?: return AllIcons.Nodes.Folder
        return ColorIcon(COLOUR_DOT_SIZE, colour, true)
    }

    override fun getTextPresentation(): String = group.name

    /** Before repository, module and directory nodes, and before ungrouped files. */
    override fun getSortWeight(): Int = REPOSITORY_SORT_WEIGHT - 1

    override fun compareUserObjects(o2: StagingGroup): Int = group.order.compareTo(o2.order)

    private companion object {
        const val COLOUR_DOT_SIZE = 12
    }
}
