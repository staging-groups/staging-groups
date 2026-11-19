package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.BaseChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import dev.yendrrek.staginggroups.domain.StagingGroup
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.tree.DefaultTreeModel

/**
 * Puts each assigned file under its staging group's node, above any repository, module or directory
 * grouping. The section new work lands in, such as **Unstaged** or the active changelist, lists every
 * group, so a group with no files there still shows.
 *
 * The platform creates one policy per section of each tree build, possibly off the EDT, so it reads
 * one snapshot of the groups taken when it is created.
 */
class StagingGroupsGroupingPolicy(project: Project, private val model: DefaultTreeModel) : BaseChangesGroupingPolicy() {

    private val groups = StagingGroupsStore.getInstance(project).snapshot()

    /** The group nodes under each parent node, by group ID. */
    private val groupNodes = IdentityHashMap<ChangesBrowserNode<*>, MutableMap<String, StagingGroupNode>>()

    /** The sections already checked for whether they list every group. */
    private val checkedSections = Collections.newSetFromMap(IdentityHashMap<ChangesBrowserNode<*>, Boolean>())

    override fun getParentNodeFor(
        nodePath: StaticFilePath,
        node: ChangesBrowserNode<*>,
        subtreeRoot: ChangesBrowserNode<*>,
    ): ChangesBrowserNode<*>? {
        val nextParent = nextPolicy?.getParentNodeFor(nodePath, node, subtreeRoot)
        val parent = nextParent ?: subtreeRoot
        if (parent === subtreeRoot && checkedSections.add(subtreeRoot) && listsEveryGroup(subtreeRoot)) {
            groups.groups.forEach { groupNode(it, parent) }
        }
        val group = groups.groupFor(nodePath.filePath.path) ?: return nextParent
        return groupNode(group, parent)
    }

    private fun groupNode(group: StagingGroup, parent: ChangesBrowserNode<*>): StagingGroupNode =
        groupNodes.getOrPut(parent, ::HashMap).getOrPut(group.id) {
            StagingGroupNode(group).also { groupNode ->
                groupNode.markAsHelperNode()
                model.insertNodeInto(groupNode, parent, parent.childCount)
                // Directory grouping below starts afresh in each group.
                markCachingRoot(groupNode)
            }
        }

    class Factory : ChangesGroupingPolicyFactory() {
        override fun createGroupingPolicy(project: Project, model: DefaultTreeModel) =
            StagingGroupsGroupingPolicy(project, model)
    }

    companion object {
        /** The key of this grouping in `changesGroupingPolicy` and in each view's saved grouping keys. */
        const val KEY = "staging-groups"

        /** Whether [section], a node files are grouped under, lists every group, empty ones too. */
        private fun listsEveryGroup(section: ChangesBrowserNode<*>): Boolean =
            (section.userObject as? LocalChangeList)?.isDefault == true ||
                VcsChangesView.EP_NAME.extensionList.any { it.listsEveryGroup(section) }
    }
}
