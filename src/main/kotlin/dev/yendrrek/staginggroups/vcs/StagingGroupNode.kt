package dev.yendrrek.staginggroups.vcs

import com.intellij.icons.AllIcons
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.yendrrek.staginggroups.StagingGroupsBundle
import dev.yendrrek.staginggroups.domain.StagingGroup
import dev.yendrrek.staginggroups.ui.StagingGroupIcons

/**
 * A staging group in a changes tree: a folder icon, or the group's colour dot while [useColours] is
 * on, then the name in bold and the file count. Its tooltip repeats both, for a name cut short by a
 * narrow tool window.
 */
class StagingGroupNode(val group: StagingGroup, private val useColours: Boolean) : ChangesBrowserNode<StagingGroup>(group) {

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
        renderer.icon = renderer.project?.let { StagingGroupIcons.colourDot(it, group, useColours) } ?: AllIcons.Nodes.Folder
        renderer.append(group.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        appendCount(renderer)
        renderer.toolTipText = StagingGroupsBundle.message("node.tooltip", group.name, fileCount)
    }

    override fun getTextPresentation(): String = group.name

    /** Before repository, module and directory nodes, and before ungrouped files. */
    override fun getSortWeight(): Int = REPOSITORY_SORT_WEIGHT - 1

    override fun compareUserObjects(o2: StagingGroup): Int = group.order.compareTo(o2.order)
}
