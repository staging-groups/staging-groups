package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** The sandbox opens the fixture repository with the Git staging area on. */
@ExtendWith(RemoteRobotExtension::class)
class StagingAreaUiTest {

    @Test
    fun `commit tool window shows the staging area with staged and unstaged changes`(robot: RemoteRobot) {
        robot.activateCommitToolWindow()
        val rows = robot.stagingTree().collectRows()

        assertTrue(rows.any { it.startsWith("Staged") }, "no Staged node in $rows")
        assertTrue(rows.any { it.startsWith("Unstaged") }, "no Unstaged node in $rows")
        robot.saveReviewScreenshot("staging-area")
    }
}
