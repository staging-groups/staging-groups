package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.Keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/** The **Staging Group** context submenu, from the staging tree, the Project view and an editor tab. */
@ExtendWith(RemoteRobotExtension::class)
class AssignStagingGroupUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(
            linkedMapOf(
                "Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"),
                "Refactor" to listOf("src/util/Util.kt"),
            ),
        )
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)
        robot.waitForStagingRows(*INITIAL_ROWS)
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.closeAllEditors()
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `assigning a file in the staging tree moves it to that group at once`(robot: RemoteRobot) {
        val tree = robot.stagingTree()
        tree.rightClickRow(tree.rowIndex("Bar.kt src"))
        robot.chooseFromStagingGroupMenu("Refactor", screenshot = "staging-tree-menu")

        robot.waitForStagingRowsWithin(
            REFRESH_LIMIT,
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Refactor 1 file", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
    }

    @Test
    fun `new staging group takes every selected file and refuses a name in use`(robot: RemoteRobot) {
        val tree = robot.stagingTree()
        tree.selectRows(robot, "Fresh.kt src", "README.md ui-test-fixture")
        tree.rightClickRow(tree.rowIndex("README.md ui-test-fixture"))
        robot.chooseFromStagingGroupMenu("New Staging Group…")

        val dialogXpath = "//div[@class='MyDialog' and @title='New Staging Group']"
        val name = robot.find(JTextFieldFixture::class.java, byXpath("$dialogXpath//div[@class='JTextField']"), Duration.ofSeconds(10))
        val ok = robot.find(ComponentFixture::class.java, byXpath("$dialogXpath//div[@class='JButton' and @text='OK']"), Duration.ofSeconds(5))
        assertFalse(ok.callJs<Boolean>("component.isEnabled()", true)) { "OK is enabled with an empty name" }
        Keyboard(robot).enterText(" refactor")
        // The error shows in a balloon, a window of its own outside the dialog.
        robot.find(ComponentFixture::class.java, byXpath("//div[@visible_text=\"A staging group named 'Refactor' already exists\"]"), Duration.ofSeconds(5))
        assertFalse(ok.callJs<Boolean>("component.isEnabled()", true)) { "OK is enabled with a name in use" }
        robot.saveReviewScreenshot("new-staging-group-duplicate-name")
        Keyboard(robot).selectAll()
        Keyboard(robot).enterText("Docs")
        assertEquals("Docs", name.text)
        robot.saveReviewScreenshot("new-staging-group-dialog")
        Keyboard(robot).enter()

        robot.waitForStagingRowsWithin(
            REFRESH_LIMIT,
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Docs 2 files", "Fresh.kt src", "README.md ui-test-fixture",
        )
        robot.saveReviewScreenshot("new-staging-group-created")
    }

    @Test
    fun `with no staging groups the menu says so, disabled, above New Staging Group`(robot: RemoteRobot) {
        robot.setStagingGroups(emptyMap())
        val tree = robot.stagingTree()
        tree.rightClickRow(tree.rowIndex("Bar.kt src"))
        robot.showingComponent("//div[@class='ActionMenu' and @text='Staging Group']").click()

        val noGroups = robot.showingComponent("//div[@class='ActionMenuItem' and @text='No Staging Groups']")
        assertFalse(noGroups.callJs<Boolean>("component.isEnabled()", true)) { "No Staging Groups is enabled" }
        robot.saveReviewScreenshot("staging-group-menu-empty")
        repeat(2) { Keyboard(robot).escape() }
    }

    @Test
    fun `removing a file from its staging group ungroups it`(robot: RemoteRobot) {
        val tree = robot.stagingTree()
        tree.rightClickRow(tree.rowIndex("Util.kt src/util"))
        robot.chooseFromStagingGroupMenu("Remove from Staging Group")

        robot.waitForStagingRowsWithin(
            REFRESH_LIMIT,
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src",
            "Fresh.kt src", "README.md ui-test-fixture", "Util.kt src/util",
        )
    }

    @Test
    fun `assigning a deleted file in the staging tree moves it to that group`(robot: RemoteRobot) {
        robot.deleteFromFixture("src/util/Model.kt")
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 6 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "Model.kt src/util", "README.md ui-test-fixture",
        )

        val tree = robot.stagingTree()
        tree.rightClickRow(tree.rowIndex("Model.kt src/util"))
        robot.chooseFromStagingGroupMenu("Refactor")

        robot.waitForStagingRowsWithin(
            REFRESH_LIMIT,
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 6 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 2 files", "Model.kt src/util", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
    }

    @Test
    fun `assigning a folder in the project view assigns only its changed files`(robot: RemoteRobot) {
        val projectTree = robot.selectInProjectView("src/util")
        projectTree.rightClickRow(projectTree.selectedRow())
        robot.chooseFromStagingGroupMenu("Feature A", screenshot = "project-view-menu")

        // The Project view shares the Commit tool window's side: bring the staging tree back to read it.
        robot.activateCommitToolWindow()
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 3 files", "Baz.kt src", "Qux.kt src", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
        val assignments = robot.stagingGroupAssignments()
        assertEquals("Feature A", assignments["src/util/Util.kt"])
        assertTrue("src/util/Model.kt" !in assignments) { "the unchanged Model.kt was assigned: $assignments" }
    }

    @Test
    fun `assigning from an editor tab assigns that file`(robot: RemoteRobot) {
        robot.openInEditor("README.md")
        robot.find(ComponentFixture::class.java, byXpath("//div[@class='EditorTabLabel' and @accessiblename='README.md']"), Duration.ofSeconds(10))
            .rightClick()
        robot.chooseFromStagingGroupMenu("Refactor", screenshot = "editor-tab-menu")

        robot.waitForStagingRowsWithin(
            REFRESH_LIMIT,
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 2 files", "README.md ui-test-fixture",
            "Util.kt src/util", "Fresh.kt src",
        )
    }

    private companion object {
        /** How soon the tree must show a change made from a menu: the stage's acceptance check. */
        val REFRESH_LIMIT: Duration = Duration.ofSeconds(1)

        val INITIAL_ROWS = arrayOf(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
    }
}

/**
 * Reveals and selects [relativePath], relative to the project directory, in the Project view, and
 * returns the view's tree.
 */
fun RemoteRobot.selectInProjectView(relativePath: String): JTreeFixture {
    runJs(
        """
        importClass(com.intellij.openapi.project.ProjectManager)
        importClass(com.intellij.openapi.vfs.LocalFileSystem)
        importClass(com.intellij.openapi.wm.ToolWindowManager)
        importClass(com.intellij.ide.projectView.ProjectView)
        var project = ProjectManager.getInstance().getOpenProjects()[0]
        var file = LocalFileSystem.getInstance().findFileByPath(project.getBasePath() + '/$relativePath')
        ToolWindowManager.getInstance(project).getToolWindow('Project').activate(null)
        ProjectView.getInstance(project).select(null, file, true)
        """,
        true,
    )
    val tree = find(JTreeFixture::class.java, byXpath("//div[@class='MyProjectViewTree']"), Duration.ofSeconds(10))
    val name = relativePath.substringAfterLast('/')
    waitFor(Duration.ofSeconds(10), Duration.ofMillis(200), "the Project view to select $relativePath") {
        tree.selectedRow().let { it >= 0 && tree.collectRows()[it].startsWith(name) }
    }
    return tree
}

/** The tree's lead selected row, or -1 without a selection. */
fun JTreeFixture.selectedRow(): Int = callJs("component.getLeadSelectionRow()", true)

/**
 * Like [waitForStagingRows], but fails unless the tree shows [rows] within [limit] of the call. Robot
 * round trips count against the limit, so a pass is a conservative measure.
 */
fun RemoteRobot.waitForStagingRowsWithin(limit: Duration, vararg rows: String) {
    val started = System.nanoTime()
    waitForStagingRows(*rows)
    val took = Duration.ofNanos(System.nanoTime() - started)
    assertTrue(took <= limit) { "the staging tree took ${took.toMillis()} ms to update, over the ${limit.toMillis()} ms limit" }
}
