package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.SimpleChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import dev.yendrrek.staginggroups.domain.StagingGroup
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import javax.swing.tree.DefaultTreeModel

/**
 * Puts each assigned file under its staging group's node, above any repository, module or directory
 * grouping. Built for every tree rebuild, possibly off the EDT, so it reads one snapshot of the
 * groups taken when it is created.
 */
class StagingGroupsGroupingPolicy(project: Project, model: DefaultTreeModel) :
    SimpleChangesGroupingPolicy<StagingGroupsGroupingPolicy.GroupKey>(model) {

    /**
     * The value group nodes are cached by. A dedicated type, because other policies cache their
     * own values in the same per-node map.
     */
    data class GroupKey(val group: StagingGroup)

    private val groups = StagingGroupsStore.getInstance(project).snapshot()

    override fun getGroupRootValueFor(nodePath: StaticFilePath, node: ChangesBrowserNode<*>): GroupKey? =
        groups.groupFor(nodePath.filePath.path)?.let(::GroupKey)

    override fun createGroupRootNode(value: GroupKey): ChangesBrowserNode<*> =
        StagingGroupNode(value.group).also { it.markAsHelperNode() }

    class Factory : ChangesGroupingPolicyFactory() {
        override fun createGroupingPolicy(project: Project, model: DefaultTreeModel) =
            StagingGroupsGroupingPolicy(project, model)
    }

    companion object {
        /** The key of this grouping in `changesGroupingPolicy` and in each view's saved grouping keys. */
        const val KEY = "staging-groups"
    }
}
