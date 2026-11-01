package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** Staging groups following a rename made outside the IDE, such as `git mv` in a terminal. */
@ExtendWith(RemoteRobotExtension::class)
class FollowGitRenamesUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(
            linkedMapOf(
                "Feature A" to listOf("src/Foo.kt", "src/Qux.kt"),
                "Refactor" to listOf("src/util/Util.kt", "README.md"),
            ),
        )
        robot.activateCommitToolWindow()
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.setStagingAreaEnabled(true)
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `a rename staged with git mv keeps the file in its staging group`(robot: RemoteRobot) {
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)

        robot.gitMoveInFixture("README.md", "NOTES.md")

        robot.waitForAssignments(
            "src/Foo.kt" to "Feature A", "src/Qux.kt" to "Feature A",
            "src/util/Util.kt" to "Refactor", "README.md" to "Refactor", "NOTES.md" to "Refactor",
        )
        robot.waitForStagingRows(
            "Staged 4 files", "Feature A 1 file", "Foo.kt src", "Refactor 1 file", "NOTES.md - renamed from README.md ui-test-fixture", "Bar.kt src", "Baz.kt src",
            "Unstaged 5 files", "Feature A 1 file", "Qux.kt src", "Refactor 2 files", "NOTES.md ui-test-fixture", "Util.kt src/util",
            "Baz.kt src", "Fresh.kt src",
        )
        robot.saveReviewScreenshot("git-mv-staging-area")
    }

    @Test
    fun `a rename made with git mv keeps the file in its staging group in the changelist`(robot: RemoteRobot) {
        robot.setStagingAreaEnabled(false)
        robot.activateCommitToolWindow()
        val tree = robot.changesTree()
        tree.runJs("component.getGroupingSupport().setGroupingKeys(java.util.Set.of())", true)
        try {
            robot.chooseGroupBy("Staging Groups")

            robot.gitMoveInFixture("README.md", "NOTES.md")

            robot.waitForAssignments(
                "src/Foo.kt" to "Feature A", "src/Qux.kt" to "Feature A",
                "src/util/Util.kt" to "Refactor", "README.md" to "Refactor", "NOTES.md" to "Refactor",
            )
            robot.waitForChangesRows(
                "Changes 6 files", "Feature A 2 files", "Foo.kt src", "Qux.kt src", "Refactor 2 files", "NOTES.md - renamed from README.md ui-test-fixture",
                "Util.kt src/util", "Bar.kt src", "Baz.kt src",
                "Unversioned Files 1 file", "Fresh.kt src",
            )
            robot.saveReviewScreenshot("git-mv-changelist")
        } finally {
            tree.runJs("component.getGroupingSupport().setGroupingKeys(java.util.Set.of('directory'))", true)
        }
    }
}
