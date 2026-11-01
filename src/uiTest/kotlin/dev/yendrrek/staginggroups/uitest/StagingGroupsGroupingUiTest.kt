package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.Keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/** **Group By | Staging Groups** in the Git staging area. */
@ExtendWith(RemoteRobotExtension::class)
class StagingGroupsGroupingUiTest {

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
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", false)
        robot.waitForStagingRows(
            "Staged 3 files", "Bar.kt src", "Baz.kt src", "Foo.kt src",
            "Unstaged 5 files", "Baz.kt src", "Fresh.kt src", "Qux.kt src", "README.md ui-test-fixture", "Util.kt src/util",
        )
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setStagingGroups(emptyMap())
        robot.resetFixtureRepo()
    }

    @Test
    fun `group by staging groups shows group nodes under staged and unstaged`(robot: RemoteRobot) {
        robot.chooseGroupBy("Staging Groups")

        assertTrue("staging-groups" in robot.stagingTreeGroupingKeys())
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )
        robot.saveReviewScreenshot("group-by-staging-groups")
    }

    @Test
    fun `directory grouping nests inside staging groups`(robot: RemoteRobot) {
        robot.chooseGroupBy("Staging Groups")
        robot.chooseGroupBy("Directory")

        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "src 2 files", "Baz.kt", "Foo.kt", "src 1 file", "Bar.kt",
            "Unstaged 5 files", "Feature A 2 files", "src 2 files", "Baz.kt", "Qux.kt", "Refactor 1 file", "src/util 1 file", "Util.kt",
            "2 files", "src 1 file", "Fresh.kt", "README.md",
        )
        robot.saveReviewScreenshot("staging-groups-with-directories")
    }

    @Test
    fun `a group node's tooltip gives its name and file count`(robot: RemoteRobot) {
        robot.chooseGroupBy("Staging Groups")
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )

        robot.stagingTree().hoverRow("Feature A 2 files", occurrence = 1)

        robot.find(ComponentFixture::class.java, byXpath("//div[@visible_text=\"Staging group 'Feature A': 2 files\"]"), Duration.ofSeconds(10))
        robot.saveReviewScreenshot("group-node-tooltip")
    }

    @Test
    fun `staging a group node stages every file in it`(robot: RemoteRobot) {
        robot.chooseGroupBy("Staging Groups")
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 2 files", "Baz.kt src", "Foo.kt src", "Bar.kt src",
            "Unstaged 5 files", "Feature A 2 files", "Baz.kt src", "Qux.kt src", "Refactor 1 file", "Util.kt src/util",
            "Fresh.kt src", "README.md ui-test-fixture",
        )

        val tree = robot.stagingTree()
        tree.hoverRow("Refactor 1 file", occurrence = 0)
        robot.saveReviewScreenshot("group-node-hover")
        tree.rightClickPath("Unstaged", "Refactor", fullMatch = false)
        robot.find(ComponentFixture::class.java, byXpath("//div[@class='ActionMenuItem' and @text='Stage']"), Duration.ofSeconds(10)).click()

        waitFor(Duration.ofSeconds(20), Duration.ofMillis(250), "Util.kt to be staged") { "src/util/Util.kt" in stagedPaths() }
        assertEquals(setOf("src/Foo.kt", "src/Bar.kt", "src/Baz.kt", "src/util/Util.kt"), stagedPaths())
    }
}

/** Waits until the staging tree, fully expanded, shows exactly [rows] as rendered, in that order. */
fun RemoteRobot.waitForStagingRows(vararg rows: String) {
    val expected = rows.toList()
    var actual = emptyList<String>()
    try {
        waitFor(Duration.ofSeconds(30), Duration.ofMillis(100), "the staging tree to show $expected") {
            val tree = stagingTree()
            tree.expandAll()
            actual = tree.collectRows()
            actual == expected
        }
    } catch (e: Exception) {
        throw AssertionError("expected rows $expected but the staging tree showed $actual", e)
    }
}

/** Ticks or unticks an entry of the **Group By** section of the staging area's **View Options** popup. */
fun RemoteRobot.chooseGroupBy(entry: String) {
    find(ComponentFixture::class.java, byXpath("//div[@class='ActionButton' and @accessiblename='View Options']"), Duration.ofSeconds(10)).click()
    val popupList = find(JListFixture::class.java, byXpath("//div[@class='HeavyWeightWindow']//div[@class='MyList']"), Duration.ofSeconds(10))
    // The rendered cells are truncated to the popup's width, so match the list model's own text.
    val index = popupList.callJs<Int>(
        """
        var model = component.getModel()
        var found = -1
        for (var i = 0; i < model.getSize(); i++) {
            var item = model.getElementAt(i)
            var text = (typeof item.getText === 'function') ? item.getText() : '' + item
            if (('' + text) == '$entry') { found = i; break }
        }
        found
        """,
        true,
    )
    check(index >= 0) { "no '$entry' in the View Options popup" }
    popupList.clickItemAtIndex(index)
    // Toggles keep the popup open; close it as a user would.
    Keyboard(this).escape()
}

/** Moves the mouse over the middle of the [occurrence]th row rendered as [text]. */
fun JTreeFixture.hoverRow(text: String, occurrence: Int) {
    val row = rowIndex(text, occurrence)
    val centre = callJs<String>(
        """
        var bounds = component.getRowBounds($row)
        '' + Math.round(bounds.getCenterX()) + ',' + Math.round(bounds.getCenterY())
        """,
        true,
    ).split(',').map { it.toInt() }
    moveMouse(java.awt.Point(centre[0], centre[1]))
}
