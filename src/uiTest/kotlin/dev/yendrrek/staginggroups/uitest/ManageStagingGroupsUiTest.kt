package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JTableFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.Keyboard
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.event.KeyEvent
import java.time.Duration

/** **Settings | Version Control | Staging Groups**, opened from **Staging Group | Manage Staging Groups…**. */
@ExtendWith(RemoteRobotExtension::class)
class ManageStagingGroupsUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(
            linkedMapOf("Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"), "Refactor" to listOf("src/util/Util.kt")),
        )
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)
        robot.waitForStagingRows(*INITIAL_ROWS)
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.closeSettingsIfOpen()
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `group nodes show a folder icon`(robot: RemoteRobot) {
        val tree = robot.stagingTree()

        assertEquals("Folder", tree.rowIcon(tree.rowIndex("Feature A 2 files")))
        assertEquals("Folder", tree.rowIcon(tree.rowIndex("Refactor 1 file")))
    }

    @Test
    fun `Manage Staging Groups opens the settings page, listing each group by name`(robot: RemoteRobot) {
        val settings = robot.openManageStagingGroups()
        val table = settings.groupsTable()

        assertEquals(listOf(listOf("Feature A"), listOf("Refactor")), table.collectItems())
        assertEquals(1, table.columnCount)
        robot.saveReviewScreenshot("settings-page")
        settings.button("Cancel").click()
    }

    @Test
    fun `renaming and reordering apply to the staging tree, keeping each group's files`(robot: RemoteRobot) {
        val settings = robot.openManageStagingGroups()
        val table = settings.groupsTable()

        table.clickCell(1, 0)
        settings.toolbarButton("Up").click()
        table.doubleClickCell(1, 0)
        val rename = robot.find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog' and @title='Rename Staging Group']"), Duration.ofSeconds(5))
        Keyboard(robot).selectAll()
        Keyboard(robot).enterText("Feature B")
        robot.saveReviewScreenshot("rename-staging-group-dialog")
        rename.button("OK").click()
        assertEquals(listOf(listOf("Refactor"), listOf("Feature B")), table.collectItems())
        settings.button("OK").click()

        robot.waitForStagingRows(
            "Staged 3 files", "Feature B 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Refactor 1 file", "Util.kt src/util", "Feature B 2 files", "Baz.kt src", "Qux.kt src",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
        assertEquals(listOf("Refactor", "Feature B"), robot.stagingGroupList())
    }

    @Test
    fun `deleting and adding apply together, returning the deleted group's files to ungrouped`(robot: RemoteRobot) {
        val settings = robot.openManageStagingGroups()
        val table = settings.groupsTable()

        table.clickCell(1, 0)
        settings.toolbarButton("Remove").click()
        settings.toolbarButton("Add").click()
        val add = robot.find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog' and @title='New Staging Group']"), Duration.ofSeconds(5))
        Keyboard(robot).enterText("Docs")
        add.button("OK").click()
        assertEquals(listOf(listOf("Feature A"), listOf("Docs")), table.collectItems())
        settings.button("OK").click()

        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Fresh.kt src", "README.md ui-test-fixture",
            "Util.kt src/util",
        )
        assertEquals(listOf("Feature A", "Docs"), robot.stagingGroupList())
        assertEquals(setOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"), robot.stagingGroupAssignments().keys)
    }

    private companion object {
        val INITIAL_ROWS = arrayOf(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
    }
}

/** Opens **Settings** at the Staging Groups page through the staging tree's context menu. */
private fun RemoteRobot.openManageStagingGroups(): CommonContainerFixture {
    val tree = stagingTree()
    tree.rightClickRow(tree.rowIndex("Bar.kt src"))
    chooseFromStagingGroupMenu("Manage Staging Groups…")
    val settings = find(CommonContainerFixture::class.java, byXpath(SETTINGS_DIALOG), Duration.ofSeconds(10))
    settings.groupsTable()
    return settings
}

private const val SETTINGS_DIALOG = "//div[@class='MyDialog' and @title='Settings']"

private fun CommonContainerFixture.groupsTable(): JTableFixture =
    find(JTableFixture::class.java, byXpath("//div[@class='TableView']"), Duration.ofSeconds(10))

private fun CommonContainerFixture.toolbarButton(name: String): ActionButtonFixture =
    find(ActionButtonFixture::class.java, byXpath("//div[@class='ActionButton' and @accessiblename='$name']"), Duration.ofSeconds(5))

/** Closes the Settings dialog a failed test left open, discarding its edits. */
private fun RemoteRobot.closeSettingsIfOpen() {
    repeat(2) {
        if (findAll(ComponentFixture::class.java, byXpath("//div[@class='MyDialog']")).isEmpty()) return
        Keyboard(this).key(KeyEvent.VK_ESCAPE)
    }
}

/** The simple class name of the icon the tree draws on [row], such as `Folder` for a folder icon. */
private fun JTreeFixture.rowIcon(row: Int): String = callJs(
    """
    var node = component.getPathForRow($row).getLastPathComponent()
    var cell = component.getCellRenderer().getTreeCellRendererComponent(component, node, false, true, false, $row, false)
    function iconOf(c) {
        if (c instanceof com.intellij.ui.SimpleColoredComponent && c.getIcon() != null) return c.getIcon()
        var children = c.getComponents()
        for (var i = 0; i < children.length; i++) { var icon = iconOf(children[i]); if (icon != null) return icon }
        return null
    }
    var icon = iconOf(cell)
    if (icon == null) 'none'
    else if (icon === com.intellij.icons.AllIcons.Nodes.Folder) 'Folder'
    else '' + icon
    """,
    true,
)
