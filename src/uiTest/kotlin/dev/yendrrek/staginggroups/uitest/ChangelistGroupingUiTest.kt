package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/** **Group By | Staging Groups** in the Commit view's changelists, with the staging area off. */
@ExtendWith(RemoteRobotExtension::class)
class ChangelistGroupingUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(
            linkedMapOf(
                "Feature A" to listOf("src/Foo.kt", "src/Qux.kt"),
                "Refactor" to listOf("src/util/Util.kt"),
            ),
        )
        robot.setStagingAreaEnabled(false)
        robot.activateCommitToolWindow()
        robot.changesTree().runJs("component.getGroupingSupport().setGroupingKeys(java.util.Set.of())", true)
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.changesTree().runJs("component.getGroupingSupport().setGroupingKeys(java.util.Set.of('directory'))", true)
        robot.setStagingAreaEnabled(true)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `group by staging groups shows group nodes inside the changelist`(robot: RemoteRobot) {
        robot.chooseGroupBy("Staging Groups")

        robot.waitForChangesRows(*GROUPED_ROWS)
        robot.saveReviewScreenshot("changelist-grouped-by-staging-groups")
    }

    @Test
    fun `assigning a file in the changelist moves it to that group at once`(robot: RemoteRobot) {
        robot.chooseGroupBy("Staging Groups")
        robot.waitForChangesRows(*GROUPED_ROWS)

        val tree = robot.changesTree()
        tree.rightClickRow(tree.rowIndex("Bar.kt src"))
        robot.chooseFromStagingGroupMenu("Refactor", screenshot = "changelist-menu")

        val started = System.nanoTime()
        robot.waitForChangesRows(
            "Changes 6 files", "Feature A 2 files", "Foo.kt src", "Qux.kt src", "Refactor 2 files", "Bar.kt src", "Util.kt src/util",
            "Baz.kt src", "README.md ui-test-fixture",
            "Unversioned Files 1 file", "Fresh.kt src",
        )
        val took = Duration.ofNanos(System.nanoTime() - started)
        assertTrue(took <= Duration.ofSeconds(1)) { "the changelist tree took ${took.toMillis()} ms to update" }
    }

    @Test
    fun `assigning a deleted or unversioned file in the changelist moves it to that group`(robot: RemoteRobot) {
        robot.deleteFromFixture("src/util/Model.kt")
        robot.chooseGroupBy("Staging Groups")
        robot.waitForChangesRows(
            "Changes 7 files", "Feature A 2 files", "Foo.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Bar.kt src", "Baz.kt src", "Model.kt src/util", "README.md ui-test-fixture",
            "Unversioned Files 1 file", "Fresh.kt src",
        )

        val tree = robot.changesTree()
        tree.selectRows(robot, "Model.kt src/util", "Fresh.kt src")
        tree.rightClickRow(tree.rowIndex("Fresh.kt src"))
        robot.chooseFromStagingGroupMenu("Refactor")

        robot.waitForChangesRows(
            "Changes 7 files", "Feature A 2 files", "Foo.kt src", "Qux.kt src", "Refactor 2 files", "Model.kt src/util", "Util.kt src/util",
            "Bar.kt src", "Baz.kt src", "README.md ui-test-fixture",
            "Unversioned Files 1 file", "Refactor 1 file", "Fresh.kt src",
        )
    }

    @Test
    fun `assigning a directory node assigns every changed file under it`(robot: RemoteRobot) {
        robot.chooseGroupBy("Directory")
        robot.waitForChangesRows(
            "Changes 6 files", "6 files", "src 5 files", "util 1 file", "Util.kt", "Bar.kt", "Baz.kt", "Foo.kt", "Qux.kt", "README.md",
            "Unversioned Files 1 file", "src 1 file", "Fresh.kt",
        )

        val tree = robot.changesTree()
        tree.rightClickRow(tree.rowIndex("src 5 files"))
        robot.chooseFromStagingGroupMenu("Refactor")

        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the files under src to join Refactor") {
            robot.stagingGroupAssignments() == mapOf(
                "src/Bar.kt" to "Refactor", "src/Baz.kt" to "Refactor", "src/Foo.kt" to "Refactor",
                "src/Qux.kt" to "Refactor", "src/util/Util.kt" to "Refactor",
            )
        }
    }

    private companion object {
        val GROUPED_ROWS = arrayOf(
            "Changes 6 files", "Feature A 2 files", "Foo.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Bar.kt src", "Baz.kt src", "README.md ui-test-fixture",
            "Unversioned Files 1 file", "Fresh.kt src",
        )
    }
}

/** Waits until the changelist tree, fully expanded, shows exactly [rows] as rendered, in that order. */
fun RemoteRobot.waitForChangesRows(vararg rows: String) {
    val expected = rows.toList()
    var actual = emptyList<String>()
    try {
        waitFor(Duration.ofSeconds(30), Duration.ofMillis(100), "the changelist tree to show $expected") {
            val tree = changesTree()
            tree.expandAll()
            actual = tree.collectRows()
            actual == expected
        }
    } catch (e: Exception) {
        throw AssertionError("expected rows $expected but the changelist tree showed $actual", e)
    }
}
