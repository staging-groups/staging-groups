package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.vcs.changes.actions.SetChangesGroupingAction

/** **Group By | Staging Groups** in the Commit view and the staging area. */
class ToggleStagingGroupsGroupingAction : SetChangesGroupingAction() {
    override val groupingKey: String get() = StagingGroupsGroupingPolicy.KEY
}
