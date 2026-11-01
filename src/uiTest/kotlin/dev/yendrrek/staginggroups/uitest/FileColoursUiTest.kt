package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/** Grouped files tinted with their staging group's colour in editor tabs, the Project view and the staging tree. */
@ExtendWith(RemoteRobotExtension::class)
class FileColoursUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setColourSettings(useColours = false, colourTabs = true, colourTrees = true)
        robot.setStagingGroups(
            linkedMapOf("Feature A" to listOf("src/Foo.kt", "src/Baz.kt", "src/Qux.kt"), "Refactor" to listOf("src/util/Util.kt")),
            colours = mapOf("Feature A" to "Blue", "Refactor" to "Green"),
        )
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)
        robot.openInEditor("src/Bar.kt")
        robot.openInEditor("src/Foo.kt")
    }

    @AfterEach
    fun tearDown(robot: RemoteRobot) {
        if (!robot.fileColoursEnabled()) robot.setFileColoursEnabled(true)
        robot.closeAllEditors()
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("staging-groups", false)
        robot.setStagingTreeGrouping("directory", true)
        robot.setStagingGroups(emptyMap())
        robot.setColourSettings(useColours = false, colourTabs = true, colourTrees = true)
        robot.resetFixtureRepo()
    }

    @Test
    fun `with colours off, the default, no file is tinted anywhere`(robot: RemoteRobot) {
        robot.waitForTabColour("Foo.kt", "none")
        assertEquals("none", robot.projectViewColour("src/Foo.kt"))
        assertEquals("none", robot.stagingRowColour("Foo.kt src"))
    }

    @Test
    fun `with colours on, grouped files are tinted on every surface and ungrouped ones are not`(robot: RemoteRobot) {
        val blue = robot.fileColour("Blue")

        robot.setUseColours(true)

        robot.waitForTabColour("Foo.kt", blue)
        robot.waitForTabColour("Bar.kt", "none")
        assertEquals(blue, robot.projectViewColour("src/Foo.kt"))
        assertEquals("none", robot.projectViewColour("src/Bar.kt"))
        robot.saveReviewScreenshot("file-colours-project-view")
        assertEquals(blue, robot.stagingRowColour("Foo.kt src"))
        assertEquals("none", robot.stagingRowColour("Bar.kt src"))
        robot.saveReviewScreenshot("file-colours-staging-tree")
    }

    @Test
    fun `assigning a file tints its tab at once, and turning colours off clears it`(robot: RemoteRobot) {
        robot.setUseColours(true)
        robot.waitForTabColour("Foo.kt", robot.fileColour("Blue"))

        robot.setStagingGroups(
            linkedMapOf("Feature A" to listOf("src/Foo.kt"), "Refactor" to listOf("src/Bar.kt")),
            colours = mapOf("Feature A" to "Blue", "Refactor" to "Green"),
        )
        robot.waitForTabColour("Bar.kt", robot.fileColour("Green"))

        robot.setUseColours(false)
        robot.waitForTabColour("Foo.kt", "none")
        robot.waitForTabColour("Bar.kt", "none")
        assertEquals("none", robot.projectViewColour("src/Foo.kt"))
    }

    @Test
    fun `each surface follows its own setting`(robot: RemoteRobot) {
        val blue = robot.fileColour("Blue")

        robot.setColourSettings(useColours = true, colourTabs = false, colourTrees = true)
        robot.waitForTabColour("Foo.kt", "none")
        assertEquals(blue, robot.projectViewColour("src/Foo.kt"))

        robot.setColourSettings(useColours = true, colourTabs = true, colourTrees = false)
        robot.waitForTabColour("Foo.kt", blue)
        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the Project view to drop the tint") {
            robot.projectViewColour("src/Foo.kt") == "none"
        }
    }

    @Test
    fun `turning off the IDE's File Colors removes the tree tints`(robot: RemoteRobot) {
        robot.setUseColours(true)
        robot.waitForTabColour("Foo.kt", robot.fileColour("Blue"))

        robot.setFileColoursEnabled(false)

        assertEquals("none", robot.projectViewColour("src/Foo.kt"))
        assertEquals("none", robot.stagingRowColour("Foo.kt src"))
    }
}

/** Waits until the editor tab of [fileName] shows [colour], as [tabColour] reads it. */
fun RemoteRobot.waitForTabColour(fileName: String, colour: String) {
    var actual = ""
    try {
        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the $fileName tab to show $colour") {
            actual = tabColour(fileName)
            actual == colour
        }
    } catch (e: Exception) {
        throw AssertionError("expected the $fileName tab colour $colour but it was $actual", e)
    }
}

/** The file colour the Project view paints behind [relativePath], revealing it first. */
private fun RemoteRobot.projectViewColour(relativePath: String): String {
    val tree = selectInProjectView(relativePath)
    return tree.rowFileColour(tree.selectedRow())
}

/** The file colour the staging tree paints behind the first row rendered as [row]. */
private fun RemoteRobot.stagingRowColour(row: String): String {
    activateCommitToolWindow()
    val tree = stagingTree()
    tree.expandAll()
    return tree.rowFileColour(tree.rowIndex(row))
}
