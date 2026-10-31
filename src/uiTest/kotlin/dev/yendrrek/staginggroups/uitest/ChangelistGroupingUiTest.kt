package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
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

        val expected = listOf(
            "Changes 6 files", "Feature A 2 files", "Foo.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Bar.kt src", "Baz.kt src", "README.md ui-test-fixture",
            "Unversioned Files 1 file", "Fresh.kt src",
        )
        var actual = emptyList<String>()
        try {
            waitFor(Duration.ofSeconds(30), Duration.ofMillis(500), "the changelist tree to show $expected") {
                val tree = robot.changesTree()
                tree.expandAll()
                actual = tree.collectRows()
                actual == expected
            }
        } catch (e: Exception) {
            throw AssertionError("expected rows $expected but the changelist tree showed $actual", e)
        }
        robot.saveReviewScreenshot("changelist-grouped-by-staging-groups")
    }
}
