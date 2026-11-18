package dev.yendrrek.staginggroups.lifecycle

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.state.StagingGroupsListener
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/** Renames, moves and deletes made through the IDE's virtual file system, on real local files. */
class VfsTrackerTest : BasePlatformTestCase() {

    private val store get() = StagingGroupsStore.getInstance(project)

    private lateinit var root: Path
    private lateinit var base: String

    override fun setUp() {
        super.setUp()
        root = createTempDirectory("staging-groups-vfs-test")
        root.resolve("src/util").createDirectories()
        for (file in listOf("src/Foo.kt", "src/util/Util.kt", "src/util/Model.kt", "README.md")) root.resolve(file).writeText("")
        base = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)!!.path
        // The light project is shared by every test class: start from two groups.
        store.update {
            val groups = GroupsCommands.create(GroupsCommands.create(GroupsState(), "Feature A", "a"), "Refactor", "r")
            GroupsCommands.assign(GroupsCommands.assign(groups, paths("src/Foo.kt"), "a"), paths("src/util/Util.kt"), "r")
        }
    }

    override fun tearDown() {
        try {
            store.update { GroupsState() }
            root.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun `test renaming a file keeps its group on the new name and on the old one`() {
        WriteAction.runAndWait<Exception> { file("src/Foo.kt").rename(this, "Renamed.kt") }

        assertEquals(assigned("src/Foo.kt" to "a", "src/Renamed.kt" to "a", "src/util/Util.kt" to "r"), store.state.assignments)
    }

    fun `test moving a file keeps its group`() {
        WriteAction.runAndWait<Exception> { file("src/Foo.kt").move(this, file("src/util")) }

        assertEquals(assigned("src/Foo.kt" to "a", "src/util/Foo.kt" to "a", "src/util/Util.kt" to "r"), store.state.assignments)
    }

    fun `test renaming a directory keeps the group of every file under it, in one change`() {
        var announced = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            StagingGroupsListener.TOPIC,
            object : StagingGroupsListener {
                override fun groupsChanged() {
                    announced++
                }
            },
        )
        store.update { GroupsCommands.assign(it, paths("src/util/Model.kt"), "a") }
        announced = 0

        WriteAction.runAndWait<Exception> { file("src/util").rename(this, "helpers") }

        assertEquals(
            assigned(
                "src/Foo.kt" to "a",
                "src/util/Util.kt" to "r", "src/util/Model.kt" to "a",
                "src/helpers/Util.kt" to "r", "src/helpers/Model.kt" to "a",
            ),
            store.state.assignments,
        )
        assertEquals(1, announced)
    }

    fun `test deleting a file keeps its group`() {
        WriteAction.runAndWait<Exception> { file("src/util/Util.kt").delete(this) }

        assertEquals(assigned("src/Foo.kt" to "a", "src/util/Util.kt" to "r"), store.state.assignments)
    }

    fun `test renaming an ungrouped file changes nothing`() {
        val before = store.state

        WriteAction.runAndWait<Exception> { file("README.md").rename(this, "NOTES.md") }

        assertSame(before, store.state)
    }

    private fun file(relativePath: String): VirtualFile =
        requireNotNull(LocalFileSystem.getInstance().findFileByPath("$base/$relativePath")) { "no file $relativePath" }

    private fun paths(vararg relativePaths: String): List<String> = relativePaths.map { "$base/$it" }

    private fun assigned(vararg entries: Pair<String, String>): Map<String, String> =
        entries.associate { (path, group) -> "$base/$path" to group }
}
