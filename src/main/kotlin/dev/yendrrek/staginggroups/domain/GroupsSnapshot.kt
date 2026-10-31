package dev.yendrrek.staginggroups.domain

/** Read-only lookups over one immutable [GroupsState]; safe to share across threads. */
class GroupsSnapshot(val state: GroupsState) {

    private val groupsById: Map<String, StagingGroup> = state.groups.associateBy { it.id }

    /** All groups in display order. */
    val groups: List<StagingGroup> get() = state.groups

    /** The group [path] is assigned to, or `null` when it has none. */
    fun groupFor(path: String): StagingGroup? = state.assignments[path]?.let(groupsById::get)

    fun group(id: String): StagingGroup? = groupsById[id]

    /** Paths assigned to the group with [groupId]. */
    fun pathsIn(groupId: String): Set<String> =
        state.assignments.filterValues { it == groupId }.keys

    companion object {
        val EMPTY = GroupsSnapshot(GroupsState())
    }
}
