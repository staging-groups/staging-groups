package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.Keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.event.KeyEvent
import java.time.Duration

/** **Remove committed files from staging groups** on a commit made in the Commit tool window. */
@ExtendWith(RemoteRobotExtension::class)
class RemoveCommittedFilesUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(
            linkedMapOf(
                "Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"),
                "Refactor" to listOf("src/util/Util.kt", "src/util/Model.kt"),
            ),
        )
        robot.setRemoveCommittedFiles(true)
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.setStagingAreaEnabled(true)
        robot.activateCommitToolWindow()
        robot.clearCommitMessage()
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setRemoveCommittedFiles(false)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `committing the staged files takes out only those left without changes`(robot: RemoteRobot) {
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )

        robot.commitFromCommitToolWindow("Commit the staged files")

        // Foo.kt is committed in full; Baz.kt keeps an unstaged change; Model.kt was never changed.
        robot.waitForAssignments(
            "src/Baz.kt" to "Feature A", "src/Qux.kt" to "Feature A",
            "src/util/Util.kt" to "Refactor", "src/util/Model.kt" to "Refactor",
        )
        robot.waitForStagingRows(
            "Staged", "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
    }

    @Test
    fun `committing the changelist takes out every committed file`(robot: RemoteRobot) {
        robot.setStagingAreaEnabled(false)
        robot.activateCommitToolWindow()
        val tree = robot.changesTree()
        tree.runJs("component.getGroupingSupport().setGroupingKeys(java.util.Set.of())", true)
        try {
            robot.waitForChangesRows(
                "Changes 6 files", "Bar.kt src", "Baz.kt src", "Foo.kt src", "Qux.kt src", "README.md ui-test-fixture", "Util.kt src/util",
                "Unversioned Files 1 file", "Fresh.kt src",
            )
            // Tick the changelist to include all of its changes, as a user does.
            tree.clickRow(tree.rowIndex("Changes 6 files"))
            Keyboard(robot).key(KeyEvent.VK_SPACE)

            robot.commitFromCommitToolWindow("Commit the changelist")

            // Every tracked change is committed; Model.kt was never changed.
            robot.waitForAssignments("src/util/Model.kt" to "Refactor")
        } finally {
            tree.runJs("component.getGroupingSupport().setGroupingKeys(java.util.Set.of('directory'))", true)
        }
    }
}

/** Empties the commit message box, which can keep a message after a commit. */
fun RemoteRobot.clearCommitMessage() {
    find(ComponentFixture::class.java, byXpath("//div[@class='CommitMessage']//div[@class='EditorComponentImpl']"), Duration.ofSeconds(10)).click()
    Keyboard(this).selectAll()
    Keyboard(this).key(KeyEvent.VK_DELETE)
}

/**
 * Types [message] as the commit message in the Commit tool window, replacing any text left there,
 * and clicks **Commit**.
 */
fun RemoteRobot.commitFromCommitToolWindow(message: String) {
    find(ComponentFixture::class.java, byXpath("//div[@class='CommitMessage']//div[@class='EditorComponentImpl']"), Duration.ofSeconds(10)).click()
    Keyboard(this).selectAll()
    Keyboard(this).enterText(message)
    // The option button and the button inside it share the name: either commits.
    val commit = byXpath("//div[@class='CommitActionsPanel']//div[@accessiblename='Commit' and (@class='JBOptionButton' or @class='MainButton' or @class='JButton')]")
    var button: ComponentFixture? = null
    waitFor(Duration.ofSeconds(10), Duration.ofMillis(100), "the Commit button") {
        button = findAll(ComponentFixture::class.java, commit).firstOrNull { it.callJs("component.isShowing() && component.isEnabled()", true) }
        button != null
    }
    button!!.click()
}
