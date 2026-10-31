package dev.yendrrek.staginggroups.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssignTargetsTest {

    private val changed = listOf("/p/src/Foo.kt", "/p/src/util/Util.kt", "/p/srcgen/Gen.kt", "/p/README.md")

    @Test
    fun `selected files are targets whether changed or not`() {
        val targets = AssignTargets.resolve(listOf(file("/p/src/Foo.kt"), file("/p/src/Unchanged.kt"))) { error("not needed") }

        assertEquals(listOf("/p/src/Foo.kt", "/p/src/Unchanged.kt"), targets)
    }

    @Test
    fun `a folder stands for the changed files under it at any depth, and no sibling folder sharing its prefix`() {
        val targets = AssignTargets.resolve(listOf(folder("/p/src"))) { changed }

        assertEquals(listOf("/p/src/Foo.kt", "/p/src/util/Util.kt"), targets)
    }

    @Test
    fun `a folder without changes stands for nothing`() {
        assertTrue(AssignTargets.resolve(listOf(folder("/p/docs"))) { changed }.isEmpty())
    }

    @Test
    fun `the file system root stands for every changed file`() {
        assertEquals(changed, AssignTargets.resolve(listOf(folder("/"))) { changed })
    }

    @Test
    fun `targets keep selection order without duplicates`() {
        val targets = AssignTargets.resolve(listOf(file("/p/README.md"), folder("/p/src"), file("/p/src/Foo.kt"))) { changed }

        assertEquals(listOf("/p/README.md", "/p/src/Foo.kt", "/p/src/util/Util.kt"), targets)
    }

    @Test
    fun `changed files are read once and only for a folder`() {
        var reads = 0
        val read = { reads++; changed }

        AssignTargets.resolve(listOf(file("/p/src/Foo.kt")), read)
        assertEquals(0, reads)

        AssignTargets.resolve(listOf(folder("/p/src"), folder("/p/srcgen")), read)
        assertEquals(1, reads)
    }

    @Test
    fun `a path is at or under itself and its ancestors only`() {
        assertTrue("/p/src".isAtOrUnder("/p/src"))
        assertTrue("/p/src/a/b.kt".isAtOrUnder("/p/src"))
        assertTrue("C:/p/a.kt".isAtOrUnder("C:/"))
        assertFalse("/p/srcgen/a.kt".isAtOrUnder("/p/src"))
        assertFalse("/p".isAtOrUnder("/p/src"))
    }

    private fun file(path: String) = SelectedPath(path, isDirectory = false)

    private fun folder(path: String) = SelectedPath(path, isDirectory = true)
}
