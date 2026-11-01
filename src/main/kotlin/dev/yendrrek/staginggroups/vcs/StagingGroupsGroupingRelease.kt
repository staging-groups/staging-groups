package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Window
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Takes **Group By | Staging Groups** out of every changes tree before the plugin unloads, and puts it
 * back where it was when the plugin loads again.
 *
 * The platform drops the grouping policy without telling the trees: a tree still grouping by it
 * keeps the plugin's group nodes, and so its class loader, and fails every later rebuild with
 * "Unknown grouping". Turning the grouping off rebuilds the tree without it. Call both on the EDT.
 */
internal object StagingGroupsGroupingRelease {

    /**
     * Marks a tree to group by staging group again on load. A string key and a Boolean value, both
     * JDK classes, so the mark keeps nothing of the plugin's alive.
     */
    private const val REGROUP_ON_LOAD = "dev.yendrrek.staginggroups.regroupOnLoad"

    fun release() {
        for (tree in changesTrees()) {
            val grouping = tree.groupingSupport
            if (!grouping.get(StagingGroupsGroupingPolicy.KEY)) continue
            tree.putClientProperty(REGROUP_ON_LOAD, true)
            grouping.set(StagingGroupsGroupingPolicy.KEY, false)
        }
    }

    fun restore() {
        for (tree in changesTrees()) {
            if (tree.getClientProperty(REGROUP_ON_LOAD) != true) continue
            tree.putClientProperty(REGROUP_ON_LOAD, null)
            if (tree.groupingSupport.isAvailable(StagingGroupsGroupingPolicy.KEY)) {
                tree.groupingSupport.set(StagingGroupsGroupingPolicy.KEY, true)
            }
        }
    }

    /** Every changes tree in a window, or in a tool window's content while it is hidden. */
    private fun changesTrees(): Collection<ChangesTree> {
        val roots = Window.getWindows().toMutableList<Component>()
        for (project in ProjectManager.getInstanceIfCreated()?.openProjects.orEmpty()) {
            if (project.isDisposed) continue
            val toolWindows = ToolWindowManager.getInstance(project)
            for (id in toolWindows.toolWindowIds) {
                toolWindows.getToolWindow(id)?.contentManagerIfCreated?.contents?.mapTo(roots) { it.component }
            }
        }
        val trees = Collections.newSetFromMap(IdentityHashMap<ChangesTree, Boolean>())
        for (root in roots) UIUtil.uiTraverser(root).filter(ChangesTree::class.java).forEach { trees += it }
        return trees
    }
}
