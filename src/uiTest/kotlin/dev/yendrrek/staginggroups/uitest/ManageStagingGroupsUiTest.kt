package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JCheckboxFixture
import com.intellij.remoterobot.fixtures.JTableFixture
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
import java.awt.event.KeyEvent
import java.time.Duration

/** **Settings | Version Control | Staging Groups**, opened from **Staging Group | Manage Staging Groups…**. */
@ExtendWith(RemoteRobotExtension::class)
class ManageStagingGroupsUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setUseColours(false)
        robot.setStagingGroups(
            linkedMapOf("Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"), "Refactor" to listOf("src/util/Util.kt")),
            colours = mapOf("Feature A" to "Blue", "Refactor" to "Green"),
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
        robot.setUseColours(false)
        robot.resetFixtureRepo()
    }

    @Test
    fun `Manage Staging Groups opens the settings page, whose colour column follows Use colours`(robot: RemoteRobot) {
        val settings = robot.openManageStagingGroups()
        val table = settings.groupsTable()
        assertEquals(listOf(listOf("Feature A"), listOf("Refactor")), table.collectItems())
        assertFalse(settings.checkBox("Colour editor tabs").isEnabled())
        robot.saveReviewScreenshot("settings-page")

        settings.checkBox("Use colours for staging groups").select()

        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the colour column") { table.columnCount == 2 }
        assertEquals(listOf(listOf("Feature A", "Blue"), listOf("Refactor", "Green")), table.collectItems())
        assertTrue(settings.checkBox("Colour editor tabs").isEnabled())
        robot.saveReviewScreenshot("settings-page-colours")
        settings.button("Cancel").click()
        assertFalse(robot.useColours())
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
        assertEquals(listOf("Refactor:Green", "Feature B:Blue"), robot.stagingGroupList())
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
        // The new group takes the first colour no other group uses.
        assertEquals(listOf("Feature A:Blue", "Docs:Green"), robot.stagingGroupList())
        assertEquals(setOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"), robot.stagingGroupAssignments().keys)
    }

    @Test
    fun `Use colours swaps the folder icons for colour dots and back, without a restart`(robot: RemoteRobot) {
        val tree = robot.stagingTree()
        assertEquals("Folder", tree.rowIcon(tree.rowIndex("Feature A 2 files")))

        robot.openManageStagingGroups().apply {
            checkBox("Use colours for staging groups").select()
            button("OK").click()
        }
        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "colour dots on group nodes") {
            tree.rowIcon(tree.rowIndex("Feature A 2 files")) == "ColorIcon"
        }
        assertEquals("ColorIcon", tree.rowIcon(tree.rowIndex("Refactor 1 file")))
        robot.saveReviewScreenshot("staging-tree-colour-dots")

        robot.openManageStagingGroups().apply {
            checkBox("Use colours for staging groups").unselect()
            button("OK").click()
        }
        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "folder icons on group nodes") {
            tree.rowIcon(tree.rowIndex("Feature A 2 files")) == "Folder"
        }
    }

    @Test
    fun `recolouring from the colour cell saves the chosen File Colors colour`(robot: RemoteRobot) {
        robot.setUseColours(true)
        val settings = robot.openManageStagingGroups()
        val table = settings.groupsTable()

        table.clickCell(0, 1)
        robot.find(ComponentFixture::class.java, byXpath("//div[@class='MyList']"), Duration.ofSeconds(5))
        robot.saveReviewScreenshot("settings-colour-popup")
        // By keyboard: remote-robot's clicks never reach this popup, though a real pointer's do.
        Keyboard(robot).enterText("Violet")
        Keyboard(robot).enter()
        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the table to show the chosen colour") {
            table.collectItems() == listOf(listOf("Feature A", "Violet"), listOf("Refactor", "Green"))
        }
        settings.button("OK").click()

        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the new colour to be saved") {
            robot.stagingGroupList() == listOf("Feature A:Violet", "Refactor:Green")
        }
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

private fun CommonContainerFixture.checkBox(text: String): JCheckboxFixture =
    find(JCheckboxFixture::class.java, byXpath("//div[@class='JBCheckBox' and @accessiblename='$text']"), Duration.ofSeconds(5))

private fun ComponentFixture.isEnabled(): Boolean = callJs("component.isEnabled()", true)

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
    else if (icon instanceof com.intellij.util.ui.ColorIcon) 'ColorIcon'
    else if (icon === com.intellij.icons.AllIcons.Nodes.Folder) 'Folder'
    else '' + icon
    """,
    true,
)
