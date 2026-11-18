package dev.yendrrek.staginggroups.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupsCommandsTest {

    private val twoGroups = GroupsState()
        .let { GroupsCommands.create(it, "Feature A", id = "a") }
        .let { GroupsCommands.create(it, "Refactor", id = "r") }

    @Test
    fun `create appends a group with a trimmed name and the next order`() {
        val state = GroupsCommands.create(twoGroups, "  Docs  ", id = "d")

        assertEquals(StagingGroup("d", "Docs", 2), state.groups.last())
        assertEquals(listOf("a", "r", "d"), state.groups.map { it.id })
    }

    @Test
    fun `create generates distinct IDs`() {
        val state = GroupsCommands.create(GroupsCommands.create(GroupsState(), "One"), "Two")

        assertEquals(2, state.groups.map { it.id }.distinct().size)
    }

    @Test
    fun `create rejects blank and duplicate names`() {
        assertThrows<IllegalArgumentException> { GroupsCommands.create(twoGroups, "   ") }
        assertThrows<IllegalArgumentException> { GroupsCommands.create(twoGroups, "feature a") }
    }

    @Test
    fun `create rejects an ID already in use`() {
        assertThrows<IllegalArgumentException> { GroupsCommands.create(twoGroups, "Other", id = "a") }
    }

    @Test
    fun `nameProblem reports blank and case-insensitive duplicates, ignoring the group itself`() {
        assertEquals(NameProblem.BLANK, GroupsCommands.nameProblem(twoGroups, " "))
        assertEquals(NameProblem.DUPLICATE, GroupsCommands.nameProblem(twoGroups, " REFACTOR "))
        assertNull(GroupsCommands.nameProblem(twoGroups, "refactor", exceptId = "r"))
        assertNull(GroupsCommands.nameProblem(twoGroups, "auth_flow"))
    }

    @Test
    fun `namesake finds the group already called a name, ignoring case, spaces and the group itself`() {
        assertEquals("r", GroupsCommands.namesake(twoGroups, " REFACTOR ")?.id)
        assertNull(GroupsCommands.namesake(twoGroups, "refactor", exceptId = "r"))
        assertNull(GroupsCommands.namesake(twoGroups, "Refactoring"))
    }

    @Test
    fun `rename changes only the name and allows a case change of its own name`() {
        val state = GroupsCommands.rename(twoGroups, "r", "REFACTOR")

        assertEquals(StagingGroup("r", "REFACTOR", 1), state.groups[1])
        assertThrows<IllegalArgumentException> { GroupsCommands.rename(twoGroups, "r", "Feature A") }
        assertThrows<IllegalArgumentException> { GroupsCommands.rename(twoGroups, "missing", "X") }
    }

    @Test
    fun `delete removes the group and its assignments and renumbers the rest`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt", "/p/Bar.kt"), "a"),
            listOf("/p/Util.kt"),
            "r",
        )

        val state = GroupsCommands.delete(assigned, "a")

        assertEquals(listOf(StagingGroup("r", "Refactor", 0)), state.groups)
        assertEquals(mapOf("/p/Util.kt" to "r"), state.assignments)
    }

    @Test
    fun `reorder moves a group and renumbers, clamping the index`() {
        val three = GroupsCommands.create(twoGroups, "Docs", id = "d")

        assertEquals(listOf("d", "a", "r"), GroupsCommands.reorder(three, "d", 0).groups.map { it.id })
        assertEquals(listOf("r", "d", "a"), GroupsCommands.reorder(three, "a", 99).groups.map { it.id })
        assertEquals(listOf(0, 1, 2), GroupsCommands.reorder(three, "a", -5).groups.map { it.order })
    }

    @Test
    fun `replaceGroups renames, reorders, adds and deletes in one edit`() {
        val assigned = GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt"), "a")
            .let { GroupsCommands.assign(it, listOf("/p/Util.kt"), "r") }

        val state = GroupsCommands.replaceGroups(
            assigned,
            listOf(StagingGroup("n", " Docs ", 7), StagingGroup("a", "Feature B", 0)),
        )

        assertEquals(listOf(StagingGroup("n", "Docs", 0), StagingGroup("a", "Feature B", 1)), state.groups)
        assertEquals(mapOf("/p/Foo.kt" to "a"), state.assignments)
    }

    @Test
    fun `replaceGroups lets two groups swap names`() {
        val state = GroupsCommands.replaceGroups(
            twoGroups,
            listOf(StagingGroup("a", "Refactor", 0), StagingGroup("r", "Feature A", 1)),
        )

        assertEquals(listOf("Refactor", "Feature A"), state.groups.map { it.name })
    }

    @Test
    fun `replaceGroups rejects blank and duplicate names and a repeated ID`() {
        assertThrows<IllegalArgumentException> { GroupsCommands.replaceGroups(twoGroups, listOf(StagingGroup("a", " ", 0))) }
        assertThrows<IllegalArgumentException> {
            GroupsCommands.replaceGroups(twoGroups, listOf(StagingGroup("a", "Docs", 0), StagingGroup("r", "docs ", 1)))
        }
        assertThrows<IllegalArgumentException> {
            GroupsCommands.replaceGroups(twoGroups, listOf(StagingGroup("a", "One", 0), StagingGroup("a", "Two", 1)))
        }
    }

    @Test
    fun `assign puts files in one group, moving them from any other`() {
        val first = GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt", "/p/Bar.kt"), "a")
        val moved = GroupsCommands.assign(first, listOf("/p/Bar.kt"), "r")

        assertEquals(mapOf("/p/Foo.kt" to "a", "/p/Bar.kt" to "r"), moved.assignments)
        assertThrows<IllegalArgumentException> { GroupsCommands.assign(twoGroups, listOf("/p/X.kt"), "missing") }
    }

    @Test
    fun `unassign removes only the given files`() {
        val assigned = GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt", "/p/Bar.kt"), "a")

        assertEquals(mapOf("/p/Bar.kt" to "a"), GroupsCommands.unassign(assigned, listOf("/p/Foo.kt", "/p/None.kt")).assignments)
    }

    @Test
    fun `followMove assigns a file and every file under a directory anew, but not a sibling with a shared prefix`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/src/A.kt", "/p/src2/C.kt", "/p/README.md"), "a"),
            listOf("/p/src/sub/B.kt"),
            "r",
        )

        val renamedDirectory = GroupsCommands.followMove(assigned, "/p/src", "/p/main")
        assertEquals(
            assigned.assignments + mapOf("/p/main/A.kt" to "a", "/p/main/sub/B.kt" to "r"),
            renamedDirectory.assignments,
        )

        val renamedFile = GroupsCommands.followMove(assigned, "/p/README.md", "/p/docs/README.md")
        assertEquals(assigned.assignments + ("/p/docs/README.md" to "a"), renamedFile.assignments)
    }

    @Test
    fun `followMove keeps the old paths assigned, for the deletions the VCS lists until the move is committed`() {
        val assigned = GroupsCommands.assign(twoGroups, listOf("/p/A.kt", "/p/dir/X.kt"), "a")

        val moved = GroupsCommands.followMove(assigned, "/p/dir", "/p/other")

        assertEquals("a", moved.assignments["/p/dir/X.kt"])
        assertEquals("a", moved.assignments["/p/other/X.kt"])
    }

    @Test
    fun `followMove replaces what was assigned at a new path and keeps the rest of the destination`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/src/A.kt"), "a"),
            listOf("/p/main/A.kt", "/p/main/Deleted.kt"),
            "r",
        )

        assertEquals(
            mapOf("/p/src/A.kt" to "a", "/p/main/A.kt" to "a", "/p/main/Deleted.kt" to "r"),
            GroupsCommands.followMove(assigned, "/p/src", "/p/main").assignments,
        )
    }

    @Test
    fun `followMove of an unassigned path, or onto itself, returns the same state`() {
        val assigned = GroupsCommands.assign(twoGroups, listOf("/p/A.kt"), "a")

        assertSame(assigned, GroupsCommands.followMove(assigned, "/p/B.kt", "/p/C.kt"))
        assertSame(assigned, GroupsCommands.followMove(assigned, "/p/A.kt", "/p/A.kt"))
    }

    @Test
    fun `followRename puts the new path in the old path's group and keeps the old path assigned`() {
        val assigned = GroupsCommands.assign(twoGroups, listOf("/p/README.md"), "r")

        val renamed = GroupsCommands.followRename(assigned, "/p/README.md", "/p/NOTES.md")

        assertEquals(mapOf("/p/README.md" to "r", "/p/NOTES.md" to "r"), renamed.assignments)
    }

    @Test
    fun `followRename leaves a new path already in a group, and an unassigned old path, alone`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/A.kt"), "a"),
            listOf("/p/B.kt"),
            "r",
        )

        assertSame(assigned, GroupsCommands.followRename(assigned, "/p/A.kt", "/p/B.kt"))
        assertSame(assigned, GroupsCommands.followRename(assigned, "/p/C.kt", "/p/D.kt"))
    }

    @Test
    fun `retain unassigns every other path and keeps the groups`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/A.kt", "/p/B.kt"), "a"),
            listOf("/p/C.kt"),
            "r",
        )

        val retained = GroupsCommands.retain(assigned, setOf("/p/B.kt", "/p/Unassigned.kt"))

        assertEquals(mapOf("/p/B.kt" to "a"), retained.assignments)
        assertEquals(twoGroups.groups, retained.groups)
        assertSame(assigned, GroupsCommands.retain(assigned, setOf("/p/A.kt", "/p/B.kt", "/p/C.kt")))
    }

    @Test
    fun `snapshot looks up a file's group and a group's files`() {
        val snapshot = GroupsSnapshot(GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt", "/p/Bar.kt"), "a"))

        assertEquals("Feature A", snapshot.groupFor("/p/Foo.kt")?.name)
        assertNull(snapshot.groupFor("/p/Other.kt"))
        assertEquals(setOf("/p/Foo.kt", "/p/Bar.kt"), snapshot.pathsIn("a"))
        assertEquals(emptySet<String>(), snapshot.pathsIn("r"))
    }
}
