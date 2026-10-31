package dev.yendrrek.staginggroups.domain

import java.util.UUID

/** Why a staging group name cannot be used. */
enum class NameProblem { BLANK, DUPLICATE }

/**
 * Every change to a [GroupsState], as pure functions. Names are trimmed; a command given a blank or
 * duplicate name, or an unknown group ID, throws [IllegalArgumentException]. Check names with
 * [nameProblem] first.
 */
object GroupsCommands {

    /** What is wrong with [name] for a group, ignoring the group [exceptId] itself; `null` if nothing. */
    fun nameProblem(state: GroupsState, name: String, exceptId: String? = null): NameProblem? = when {
        name.isBlank() -> NameProblem.BLANK
        namesake(state, name, exceptId) != null -> NameProblem.DUPLICATE
        else -> null
    }

    /**
     * The group other than [exceptId] already called [name], comparing without case or surrounding
     * whitespace; `null` if none.
     */
    fun namesake(state: GroupsState, name: String, exceptId: String? = null): StagingGroup? {
        val trimmed = name.trim()
        return state.groups.firstOrNull { it.id != exceptId && it.name.equals(trimmed, ignoreCase = true) }
    }

    /**
     * The colour for a new group: the first of [palette] no group uses yet, or, once all are taken,
     * the palette again from the start. Empty when [palette] is.
     */
    fun nextColourId(state: GroupsState, palette: List<String>): String {
        if (palette.isEmpty()) return ""
        val used = state.groups.mapTo(HashSet()) { it.colourId }
        return palette.firstOrNull { it !in used } ?: palette[state.groups.size % palette.size]
    }

    /** Adds a group named [name] after all others. */
    fun create(state: GroupsState, name: String, colourId: String, id: String = UUID.randomUUID().toString()): GroupsState {
        requireValidName(state, name, exceptId = null)
        require(state.groups.none { it.id == id }) { "Staging group ID $id is already used" }
        val group = StagingGroup(id = id, name = name.trim(), colourId = colourId, order = state.groups.size)
        return state.copy(groups = state.groups + group)
    }

    fun rename(state: GroupsState, id: String, name: String): GroupsState {
        requireValidName(state, name, exceptId = id)
        return state.updateGroup(id) { it.copy(name = name.trim()) }
    }

    fun recolour(state: GroupsState, id: String, colourId: String): GroupsState =
        state.updateGroup(id) { it.copy(colourId = colourId) }

    /** Removes the group and unassigns its files. */
    fun delete(state: GroupsState, id: String): GroupsState {
        requireGroup(state, id)
        return GroupsState(
            groups = numbered(state.groups.filter { it.id != id }),
            assignments = state.assignments.filterValues { it != id },
        )
    }

    /** Moves the group to position [index] (clamped to the valid range), shifting the others. */
    fun reorder(state: GroupsState, id: String, index: Int): GroupsState {
        val group = requireGroup(state, id)
        val others = state.groups.filter { it.id != id }.toMutableList()
        others.add(index.coerceIn(0, others.size), group)
        return state.copy(groups = numbered(others))
    }

    /** Assigns [paths] to the group, moving any already in another group. */
    fun assign(state: GroupsState, paths: Collection<String>, id: String): GroupsState {
        requireGroup(state, id)
        return state.copy(assignments = state.assignments + paths.associateWith { id })
    }

    fun unassign(state: GroupsState, paths: Collection<String>): GroupsState =
        state.copy(assignments = state.assignments - paths.toSet())

    /**
     * Follows a rename or move from [oldPath] to [newPath]: the assignment of that path and of every
     * path under it (when it is a directory) moves to the new location. As on disk, the moved
     * entries replace whatever was assigned at the destination.
     */
    fun movePath(state: GroupsState, oldPath: String, newPath: String): GroupsState {
        if (oldPath == newPath) return state
        val (moving, staying) = state.assignments.entries.partition { (path, _) -> path.isAtOrUnder(oldPath) }
        if (moving.isEmpty()) return state
        val moved = moving.associate { (path, id) -> newPath + path.substring(oldPath.length) to id }
        val kept = staying.filterNot { (path, _) -> path.isAtOrUnder(newPath) }.associate { it.key to it.value }
        return state.copy(assignments = kept + moved)
    }

    private fun requireValidName(state: GroupsState, name: String, exceptId: String?) {
        val problem = nameProblem(state, name, exceptId)
        require(problem == null) { "Invalid staging group name '$name': $problem" }
    }

    private fun requireGroup(state: GroupsState, id: String): StagingGroup =
        requireNotNull(state.groups.firstOrNull { it.id == id }) { "No staging group with ID $id" }

    private fun GroupsState.updateGroup(id: String, change: (StagingGroup) -> StagingGroup): GroupsState {
        requireGroup(this, id)
        return copy(groups = groups.map { if (it.id == id) change(it) else it })
    }

    private fun numbered(groups: List<StagingGroup>): List<StagingGroup> =
        groups.mapIndexed { index, group -> if (group.order == index) group else group.copy(order = index) }
}
