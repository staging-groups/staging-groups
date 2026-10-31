package dev.yendrrek.staginggroups.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupsCommandsTest {

    private val twoGroups = GroupsState()
        .let { GroupsCommands.create(it, "Feature A", "Blue", id = "a") }
        .let { GroupsCommands.create(it, "Refactor", "Green", id = "r") }

    @Test
    fun `create appends a group with a trimmed name and the next order`() {
        val state = GroupsCommands.create(twoGroups, "  Docs  ", "Rose", id = "d")

        assertEquals(StagingGroup("d", "Docs", "Rose", 2), state.groups.last())
        assertEquals(listOf("a", "r", "d"), state.groups.map { it.id })
    }

    @Test
    fun `create generates distinct IDs`() {
        val state = GroupsCommands.create(GroupsCommands.create(GroupsState(), "One", ""), "Two", "")

        assertEquals(2, state.groups.map { it.id }.distinct().size)
    }

    @Test
    fun `create rejects blank and duplicate names`() {
        assertThrows<IllegalArgumentException> { GroupsCommands.create(twoGroups, "   ", "Blue") }
        assertThrows<IllegalArgumentException> { GroupsCommands.create(twoGroups, "feature a", "Blue") }
    }

    @Test
    fun `create rejects an ID already in use`() {
        assertThrows<IllegalArgumentException> { GroupsCommands.create(twoGroups, "Other", "Blue", id = "a") }
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

        assertEquals(StagingGroup("r", "REFACTOR", "Green", 1), state.groups[1])
        assertThrows<IllegalArgumentException> { GroupsCommands.rename(twoGroups, "r", "Feature A") }
        assertThrows<IllegalArgumentException> { GroupsCommands.rename(twoGroups, "missing", "X") }
    }

    @Test
    fun `recolour changes only the colour`() {
        val state = GroupsCommands.recolour(twoGroups, "a", "Violet")

        assertEquals(StagingGroup("a", "Feature A", "Violet", 0), state.groups[0])
    }

    @Test
    fun `delete removes the group and its assignments and renumbers the rest`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt", "/p/Bar.kt"), "a"),
            listOf("/p/Util.kt"),
            "r",
        )

        val state = GroupsCommands.delete(assigned, "a")

        assertEquals(listOf(StagingGroup("r", "Refactor", "Green", 0)), state.groups)
        assertEquals(mapOf("/p/Util.kt" to "r"), state.assignments)
    }

    @Test
    fun `reorder moves a group and renumbers, clamping the index`() {
        val three = GroupsCommands.create(twoGroups, "Docs", "Rose", id = "d")

        assertEquals(listOf("d", "a", "r"), GroupsCommands.reorder(three, "d", 0).groups.map { it.id })
        assertEquals(listOf("r", "d", "a"), GroupsCommands.reorder(three, "a", 99).groups.map { it.id })
        assertEquals(listOf(0, 1, 2), GroupsCommands.reorder(three, "a", -5).groups.map { it.order })
    }

    @Test
    fun `replaceGroups renames, recolours, reorders, adds and deletes in one edit`() {
        val assigned = GroupsCommands.assign(twoGroups, listOf("/p/Foo.kt"), "a")
            .let { GroupsCommands.assign(it, listOf("/p/Util.kt"), "r") }

        val state = GroupsCommands.replaceGroups(
            assigned,
            listOf(StagingGroup("n", " Docs ", "Rose", 7), StagingGroup("a", "Feature B", "Violet", 0)),
        )

        assertEquals(listOf(StagingGroup("n", "Docs", "Rose", 0), StagingGroup("a", "Feature B", "Violet", 1)), state.groups)
        assertEquals(mapOf("/p/Foo.kt" to "a"), state.assignments)
    }

    @Test
    fun `replaceGroups lets two groups swap names`() {
        val state = GroupsCommands.replaceGroups(
            twoGroups,
            listOf(StagingGroup("a", "Refactor", "Blue", 0), StagingGroup("r", "Feature A", "Green", 1)),
        )

        assertEquals(listOf("Refactor", "Feature A"), state.groups.map { it.name })
    }

    @Test
    fun `replaceGroups rejects blank and duplicate names and a repeated ID`() {
        assertThrows<IllegalArgumentException> { GroupsCommands.replaceGroups(twoGroups, listOf(StagingGroup("a", " ", "", 0))) }
        assertThrows<IllegalArgumentException> {
            GroupsCommands.replaceGroups(twoGroups, listOf(StagingGroup("a", "Docs", "", 0), StagingGroup("r", "docs ", "", 1)))
        }
        assertThrows<IllegalArgumentException> {
            GroupsCommands.replaceGroups(twoGroups, listOf(StagingGroup("a", "One", "", 0), StagingGroup("a", "Two", "", 1)))
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
    fun `movePath follows a file and every file under a directory, but not a sibling with a shared prefix`() {
        val assigned = GroupsCommands.assign(
            twoGroups,
            listOf("/p/src/A.kt", "/p/src/sub/B.kt", "/p/src2/C.kt", "/p/README.md"),
            "a",
        )

        val renamedDirectory = GroupsCommands.movePath(assigned, "/p/src", "/p/main")
        assertEquals(
            setOf("/p/main/A.kt", "/p/main/sub/B.kt", "/p/src2/C.kt", "/p/README.md"),
            renamedDirectory.assignments.keys,
        )

        val renamedFile = GroupsCommands.movePath(assigned, "/p/README.md", "/p/docs/README.md")
        assertEquals("a", renamedFile.assignments["/p/docs/README.md"])
        assertNull(renamedFile.assignments["/p/README.md"])
    }

    @Test
    fun `movePath replaces whatever was assigned at the destination`() {
        val assigned = GroupsCommands.assign(
            GroupsCommands.assign(twoGroups, listOf("/p/A.kt"), "a"),
            listOf("/p/B.kt", "/p/dir/X.kt"),
            "r",
        )

        assertEquals(mapOf("/p/B.kt" to "a", "/p/dir/X.kt" to "r"), GroupsCommands.movePath(assigned, "/p/A.kt", "/p/B.kt").assignments)
        assertEquals(mapOf("/p/dir" to "a", "/p/B.kt" to "r"), GroupsCommands.movePath(assigned, "/p/A.kt", "/p/dir").assignments)
    }

    @Test
    fun `movePath of an unassigned path returns the same state`() {
        assertSame(twoGroups, GroupsCommands.movePath(twoGroups, "/p/A.kt", "/p/B.kt"))
    }

    @Test
    fun `nextColourId takes the first unused palette colour, then cycles`() {
        val palette = listOf("Blue", "Green", "Rose")

        assertEquals("Rose", GroupsCommands.nextColourId(twoGroups, palette))
        val full = GroupsCommands.create(twoGroups, "Docs", "Rose", id = "d")
        assertEquals("Blue", GroupsCommands.nextColourId(full, palette))
        assertEquals("", GroupsCommands.nextColourId(full, emptyList()))
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
