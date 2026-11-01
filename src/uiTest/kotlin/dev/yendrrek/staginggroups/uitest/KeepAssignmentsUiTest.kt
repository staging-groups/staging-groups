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

/** Staging groups following their files through renames, moves, deletes and commits. */
@ExtendWith(RemoteRobotExtension::class)
class KeepAssignmentsUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(
            linkedMapOf(
                "Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"),
                "Refactor" to listOf("src/util/Util.kt", "README.md"),
            ),
        )
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Refactor 2 files", "README.md ui-test-fixture", "Util.kt src/util", "Fresh.kt src",
        )
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.closeAllEditors()
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `renaming a file keeps the new file and the old one's deletion in its staging group`(robot: RemoteRobot) {
        robot.renameInProjectView("README.md", "NOTES.md")
        robot.declineGitConfirmations(2)

        robot.activateCommitToolWindow()
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 6 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Refactor 3 files", "NOTES.md ui-test-fixture", "README.md ui-test-fixture", "Util.kt src/util", "Fresh.kt src",
        )
        robot.saveReviewScreenshot("renamed-file")
    }

    @Test
    fun `renaming a directory keeps every file under it in its staging group`(robot: RemoteRobot) {
        robot.renameInProjectView("src/util", "helpers")
        robot.declineGitConfirmations(2)

        robot.waitForAssignments(
            "src/Foo.kt" to "Feature A", "src/Baz.kt" to "Feature A", "src/Qux.kt" to "Feature A",
            "README.md" to "Refactor", "src/util/Util.kt" to "Refactor", "src/helpers/Util.kt" to "Refactor",
        )
        robot.activateCommitToolWindow()
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 8 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Refactor 3 files", "README.md ui-test-fixture", "Util.kt src/helpers", "Util.kt src/util",
            "Fresh.kt src", "Model.kt src/helpers", "Model.kt src/util",
        )
        robot.saveReviewScreenshot("renamed-directory")
    }

    @Test
    fun `moving a file keeps it in its staging group`(robot: RemoteRobot) {
        robot.moveInIde("src/Qux.kt", "src/util")
        robot.declineGitConfirmations(2)

        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 6 files", "Feature A 3 files", "Baz.kt src", "Qux.kt src/util", "Qux.kt src",
            "Refactor 2 files", "README.md ui-test-fixture", "Util.kt src/util", "Fresh.kt src",
        )
    }

    @Test
    fun `deleting a file in the project view keeps its deletion in its staging group`(robot: RemoteRobot) {
        robot.clickInProjectView("src/util/Util.kt")
        Keyboard(robot).key(KeyEvent.VK_DELETE)
        robot.find(ComponentFixture::class.java, byXpath("//div[@class='MyDialog' and @title='Delete']"), Duration.ofSeconds(10))
        Keyboard(robot).enter()
        robot.declineGitConfirmations(1)

        robot.activateCommitToolWindow()
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Refactor 2 files", "README.md ui-test-fixture", "Util.kt src/util", "Fresh.kt src",
        )
        robot.waitForAssignments(
            "src/Foo.kt" to "Feature A", "src/Baz.kt" to "Feature A", "src/Qux.kt" to "Feature A",
            "src/util/Util.kt" to "Refactor", "README.md" to "Refactor",
        )
    }

    @Test
    fun `removing unchanged files after a commit keeps every file that still has changes`(robot: RemoteRobot) {
        robot.setStagingGroups(
            linkedMapOf(
                "Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"),
                "Refactor" to listOf("src/util/Util.kt", "src/util/Model.kt", "README.md"),
            ),
        )
        // Foo.kt and Bar.kt have only staged changes; Baz.kt keeps an unstaged one.
        robot.commitStagedInFixture()
        robot.waitForStagingRows(
            "Staged", "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Refactor 2 files", "README.md ui-test-fixture", "Util.kt src/util", "Fresh.kt src",
        )

        val tree = robot.stagingTree()
        tree.rightClickRow(tree.rowIndex("Qux.kt src"))
        robot.chooseFromStagingGroupMenu("Remove Unchanged Files from Staging Groups", screenshot = "remove-unchanged-files-menu")

        robot.waitForAssignments(
            "src/Baz.kt" to "Feature A", "src/Qux.kt" to "Feature A",
            "src/util/Util.kt" to "Refactor", "README.md" to "Refactor",
        )
        robot.waitForStagingRows(
            "Staged", "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Refactor 2 files", "README.md ui-test-fixture", "Util.kt src/util", "Fresh.kt src",
        )
    }
}

/**
 * Renames [relativePath], relative to the project directory, to [newName] as a user does: click it
 * in the Project view, press Shift+F6, type the name and confirm.
 */
fun RemoteRobot.renameInProjectView(relativePath: String, newName: String) {
    clickInProjectView(relativePath)
    Keyboard(this).hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_F6)
    find(ComponentFixture::class.java, byXpath("//div[@class='MyDialog' and contains(@title, 'Rename')]"), Duration.ofSeconds(10))
    Keyboard(this).selectAll()
    Keyboard(this).enterText(newName)
    Keyboard(this).enter()
}

/**
 * Reveals [relativePath], relative to the project directory, in the Project view and clicks it, so
 * the view has the keyboard focus for a shortcut.
 */
fun RemoteRobot.clickInProjectView(relativePath: String) {
    val tree = selectInProjectView(relativePath)
    tree.clickRow(tree.selectedRow())
    waitFor(Duration.ofSeconds(10), Duration.ofMillis(100), "the Project view to take the focus") {
        tree.callJs("component.hasFocus()", true)
    }
}

/**
 * Answers Cancel to each of the [count] questions Git asks after the IDE creates or deletes files: to
 * add the files it created, such as a renamed file's new path, and to delete from Git those it
 * deleted, such as the old path. The changes stay out of the index, unstaged. Left open, a modal
 * question would hold back every later refresh of the Git views.
 */
fun RemoteRobot.declineGitConfirmations(count: Int) {
    val cancelXpath = "//div[@class='MyDialog' and contains(@accessiblename, 'Git')]//div[@class='JButton' and @text='Cancel']"
    repeat(count) {
        var cancel: ComponentFixture? = null
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(100), "Git to ask a question") {
            cancel = findAll(ComponentFixture::class.java, byXpath(cancelXpath)).firstOrNull { it.isShowing() }
            cancel != null
        }
        cancel!!.click()
        // The next question can open at once: wait for this dialog's own button to go.
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(100), "the Git question to close") { !cancel!!.isShowing() }
    }
}

/** Whether the component is on screen; a component already removed from its window is not. */
private fun ComponentFixture.isShowing(): Boolean = runCatching { callJs<Boolean>("component.isShowing()", true) }.getOrDefault(false)

/**
 * Moves [relativePath] into the directory [toDirectory], both relative to the project directory, as
 * the IDE's Move refactoring and drag and drop do on disk: one undoable command moving the file.
 */
fun RemoteRobot.moveInIde(relativePath: String, toDirectory: String) {
    runJs(
        """
        importClass(com.intellij.openapi.project.ProjectManager)
        importClass(com.intellij.openapi.vfs.LocalFileSystem)
        importClass(com.intellij.openapi.command.WriteCommandAction)
        var project = ProjectManager.getInstance().getOpenProjects()[0]
        var fs = LocalFileSystem.getInstance()
        var file = fs.findFileByPath(project.getBasePath() + '/$relativePath')
        var target = fs.findFileByPath(project.getBasePath() + '/$toDirectory')
        WriteCommandAction.runWriteCommandAction(project, new java.lang.Runnable({
            run: function () { file.move(project, target) }
        }))
        """,
        true,
    )
}

/** Waits until the staging group assignments are exactly [expected], by path relative to the project directory. */
fun RemoteRobot.waitForAssignments(vararg expected: Pair<String, String>) {
    val wanted = expected.toMap()
    var actual = emptyMap<String, String>()
    try {
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(100), "the assignments to become $wanted") {
            actual = stagingGroupAssignments()
            actual == wanted
        }
    } catch (e: Exception) {
        throw AssertionError("expected assignments $wanted but found $actual", e)
    }
}
