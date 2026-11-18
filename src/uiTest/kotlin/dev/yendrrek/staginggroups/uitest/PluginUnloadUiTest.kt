package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.Keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/**
 * Disabling the plugin without a restart, as **Settings | Plugins** does: its class loader must go,
 * and the staging tree its group nodes. Enabling it again must bring back the groups and the
 * grouping.
 *
 * The plugin is loaded back in a `finally`: the rest of the suite shares this sandbox.
 */
@ExtendWith(RemoteRobotExtension::class)
class PluginUnloadUiTest {

    @BeforeEach
    fun setUp(robot: RemoteRobot) {
        robot.resetFixtureRepo()
        robot.setStagingGroups(linkedMapOf("Feature A" to listOf("src/Foo.kt")))
        robot.activateCommitToolWindow()
        robot.setStagingTreeGrouping("directory", false)
        robot.setStagingTreeGrouping("staging-groups", true)
        robot.openInEditor("src/Foo.kt")
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
    fun `the platform can disable the plugin without a restart`(robot: RemoteRobot) {
        assertNull(robot.unloadWithoutRestartRefusal(), "the platform refuses to unload Staging Groups without a restart")
    }

    @Test
    fun `disabling releases the class loader and the grouping, and enabling brings them back`(robot: RemoteRobot) {
        // Every UI of the plugin first, each a chance to leave a reference to its classes behind:
        // the New Staging Group dialog with text selected in it (Linux puts a selection on the
        // primary clipboard), and the settings page.
        robot.waitForStagingRows(
            "Staged 3 files", "Feature A 1 file", "Foo.kt src", "Bar.kt src", "Baz.kt src",
            "Unstaged 5 files", "Baz.kt src", "Fresh.kt src", "Qux.kt src", "README.md ui-test-fixture", "Util.kt src/util",
        )
        val tree = robot.stagingTree()
        tree.clickRow(tree.rowIndex("Bar.kt src"))
        tree.rightClickRow(tree.rowIndex("Bar.kt src"))
        robot.chooseFromStagingGroupMenu("New Staging Group…")
        val dialog = robot.find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog' and @title='New Staging Group']"), Duration.ofSeconds(10))
        Keyboard(robot).enterText("Docs")
        Keyboard(robot).selectAll()
        dialog.button("OK").click()
        waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "Docs to be created") { robot.stagingGroupList().size == 2 }
        assertEquals(mapOf("src/Bar.kt" to "Docs", "src/Foo.kt" to "Feature A"), robot.stagingGroupAssignments())
        tree.rightClickRow(tree.rowIndex("Foo.kt src"))
        robot.chooseFromStagingGroupMenu("Manage Staging Groups…")
        robot.find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog' and @title='Settings']"), Duration.ofSeconds(10))
            .button("Cancel").click()
        // A tree keeps the row last pressed in it until the next release there, which a right-click
        // never sends it: the context menu takes the release. Here that row is in a staging group;
        // a user's next click in the tree lets it go.
        robot.stagingTree().clickRow(0)
        robot.useAnotherMenu()
        robot.evictRobotComponentCache()

        var unloaded = false
        try {
            val outcome = robot.unloadStagingGroups()
            unloaded = outcome.startsWith("unloaded")
            assertEquals("unloaded:collected", outcome, "':retained' is the \"not unload-safe\" dialog and the restart a user has to do")
            robot.waitForStagingRows(
                "Staged 3 files", "Bar.kt src", "Baz.kt src", "Foo.kt src",
                "Unstaged 5 files", "Baz.kt src", "Fresh.kt src", "Qux.kt src", "README.md ui-test-fixture", "Util.kt src/util",
            )

            unloaded = false
            assertEquals(true, robot.reloadStagingGroups(), "Staging Groups could not be loaded back")
            robot.waitForStagingRows(
                "Staged 3 files", "Feature A 1 file", "Foo.kt src", "Docs 1 file", "Bar.kt src", "Baz.kt src",
                "Unstaged 5 files", "Baz.kt src", "Fresh.kt src", "Qux.kt src", "README.md ui-test-fixture", "Util.kt src/util",
            )
            assertEquals(listOf("Feature A", "Docs"), robot.stagingGroupList())
            assertEquals(mapOf("src/Bar.kt" to "Docs", "src/Foo.kt" to "Feature A"), robot.stagingGroupAssignments())
        } finally {
            if (unloaded) robot.reloadStagingGroups()
        }
    }
}

/**
 * Opens the editor's context menu and closes it by clicking beside it, with a real mouse.
 *
 * On X11 the IDE frame keeps pointing at the popup window last clicked in, here the plugin's
 * **Staging Group** submenu, which holds the plugin's actions, until a click made while another popup
 * is open. Any later use of a menu does that; until then the plugin cannot unload, a JDK behaviour no
 * plugin can change. remote-robot's own clicks are posted as events, which X11 never sees.
 */
private fun RemoteRobot.useAnotherMenu() {
    pressEditor(java.awt.event.InputEvent.BUTTON3_DOWN_MASK, centre = true)
    waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the editor's context menu") { showingPopupMenus() > 0 }
    pressEditor(java.awt.event.InputEvent.BUTTON1_DOWN_MASK, centre = false)
    waitFor(Duration.ofSeconds(5), Duration.ofMillis(100), "the editor's context menu to close") { showingPopupMenus() == 0 }
}

private fun RemoteRobot.pressEditor(button: Int, centre: Boolean) {
    runJs(
        """
        importClass(java.awt.Window)
        importClass(com.intellij.openapi.editor.impl.EditorComponentImpl)
        function find(c) {
            if (c instanceof EditorComponentImpl && c.isShowing()) return c
            var children = c.getComponents ? c.getComponents() : []
            for (var i = 0; i < children.length; i++) { var found = find(children[i]); if (found != null) return found }
            return null
        }
        var editor = null
        var windows = Window.getWindows()
        for (var w = 0; w < windows.length && editor == null; w++) if (windows[w].isShowing()) editor = find(windows[w])
        var at = editor.getLocationOnScreen()
        var visible = editor.getVisibleRect()
        var x = at.x + visible.x + ($centre ? visible.width / 2 : 10)
        var y = at.y + visible.y + ($centre ? visible.height / 2 : 10)
        var mouse = new java.awt.Robot()
        mouse.mouseMove(x, y)
        mouse.mousePress($button)
        mouse.mouseRelease($button)
        """,
        true,
    )
}

/**
 * Pushes every component this test looked up out of the robot server's component cache, which holds
 * the last 1000 components found, strongly: the plugin's dialogs and menus among them would keep its
 * class loader alive. A user's IDE has no such cache. Each lookup adds a new entry.
 */
private fun RemoteRobot.evictRobotComponentCache() {
    var added = 0
    while (added <= ROBOT_COMPONENT_CACHE_SIZE) added += findAll(ComponentFixture::class.java, byXpath("//div")).size
}

private const val ROBOT_COMPONENT_CACHE_SIZE = 1000

/** How many menus are open, the submenus of an open menu included. */
private fun RemoteRobot.showingPopupMenus(): Int =
    callJs("javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length", true)

/** What the platform answers when asked whether the plugin can be unloaded without a restart: `null` if it can. */
private fun RemoteRobot.unloadWithoutRestartRefusal(): String? = callJs<String>(
    """
    importPackage(com.intellij.ide.plugins)
    importPackage(com.intellij.openapi.extensions)
    var descriptor = PluginManagerCore.getPlugin(PluginId.getId('$PLUGIN_ID'))
    var reason = DynamicPlugins.INSTANCE.checkCanUnloadWithoutRestart(descriptor)
    reason == null ? '' : '' + reason
    """,
    true,
).ifEmpty { null }

/**
 * Unloads the plugin as a Disable in **Settings | Plugins** does (mark it disabled, then unload it
 * with the default options) and reports whether the platform accepted the unload and whether the
 * class loader was then collected: `unloaded:collected` when all is well.
 *
 * Runs off the EDT and hands only the unload to it: the platform finishes releasing the plugin in
 * work it queues on the EDT, which the collection loop must not block. The class loader is held only
 * weakly here.
 */
private fun RemoteRobot.unloadStagingGroups(): String = callJs(
    """
    importPackage(com.intellij.ide.plugins)
    importPackage(com.intellij.openapi.extensions)
    importClass(com.intellij.openapi.application.ApplicationManager)
    importClass(com.intellij.openapi.application.ModalityState)
    importClass(com.intellij.openapi.project.ProjectManager)

    // The options a Disable in Settings | Plugins unloads with: saving first, no waiting for the
    // class loader, which this script measures itself without writing a heap snapshot.
    var options = java.lang.Class.forName('com.intellij.ide.plugins.DynamicPlugins${'$'}UnloadPluginOptions').getConstructor().newInstance()
    var project = ProjectManager.getInstance().getOpenProjects()[0]
    var descriptor = PluginManagerCore.getPlugin(PluginId.getId('$PLUGIN_ID'))
    var watcher = new java.util.concurrent.atomic.AtomicReference()
    var outcome = new java.util.concurrent.atomic.AtomicReference('')
    ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
        run: function () {
            // Watched from here, not from this script's own frame, where the class loader could
            // linger on the interpreter's stack and keep alive the very thing measured.
            watcher.set(com.intellij.util.ref.GCWatcher.tracking(descriptor.getPluginClassLoader()))
            // Kept for reloadStagingGroups: an unloaded plugin is no longer in PluginManagerCore.
            java.lang.System.getProperties().put('staginggroups.unloaded.descriptor', descriptor)
            PluginEnabler.HEADLESS.disable(java.util.Collections.singletonList(descriptor))
            var unloaded = DynamicPlugins.INSTANCE.unloadPlugins(java.util.Collections.singletonList(descriptor), project, null, options)
            outcome.set(unloaded ? 'unloaded' : 'refused')
        }
    }), ModalityState.nonModal())
    descriptor = null
    // Collected as the platform's own unload check collects. Up to 25 s, as the robot server's window
    // monitor (AssertJ Swing) holds every new window, such as the plugin's popup menus, for 10 s
    // after it opens; a user's IDE has none.
    var collected = false
    var deadline = java.lang.System.currentTimeMillis() + 25000
    while (!collected && java.lang.System.currentTimeMillis() < deadline) {
        collected = watcher.get().tryCollect(1000)
        if (!collected) java.lang.Thread.sleep(500)
    }
    outcome.get() + (collected ? ':collected' : ':retained')
    """,
    false,
)

/** Enables and loads the plugin again after [unloadStagingGroups]; whether the platform accepted it. */
private fun RemoteRobot.reloadStagingGroups(): Boolean = callJs<String>(
    """
    importPackage(com.intellij.ide.plugins)
    importClass(com.intellij.openapi.application.ApplicationManager)
    importClass(com.intellij.openapi.application.ModalityState)
    importClass(com.intellij.openapi.project.ProjectManager)
    var project = ProjectManager.getInstance().getOpenProjects()[0]
    var outcome = new java.util.concurrent.atomic.AtomicReference('')
    ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
        run: function () {
            var descriptor = java.lang.System.getProperties().remove('staginggroups.unloaded.descriptor')
            PluginEnabler.HEADLESS.enable(java.util.Collections.singletonList(descriptor))
            outcome.set('' + DynamicPlugins.INSTANCE.loadPlugins(java.util.Collections.singletonList(descriptor), project))
        }
    }), ModalityState.nonModal())
    outcome.get()
    """,
    false,
).toBoolean()

private const val PLUGIN_ID = "dev.yendrrek.staginggroups"
