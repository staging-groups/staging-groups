package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import java.io.File
import java.time.Duration

/** Opens and focuses the Commit tool window of the sandbox's only open project. */
fun RemoteRobot.activateCommitToolWindow() {
    runJs(
        """
        importClass(com.intellij.openapi.project.ProjectManager)
        importClass(com.intellij.openapi.wm.ToolWindowManager)
        var project = ProjectManager.getInstance().getOpenProjects()[0]
        ToolWindowManager.getInstance(project).getToolWindow('Commit').activate(null)
        """,
        true,
    )
}

/**
 * Switches the Git staging area on or off, as applying **Settings | Version Control | Git** does:
 * off, the Commit tool window shows changelists instead.
 */
fun RemoteRobot.setStagingAreaEnabled(enabled: Boolean) {
    runJs(
        """
        importClass(com.intellij.openapi.application.ApplicationManager)
        importClass(com.intellij.vcs.commit.CommitModeManager)
        importClass(com.intellij.ide.plugins.PluginManagerCore)
        importClass(com.intellij.openapi.extensions.PluginId)
        var gitLoader = PluginManagerCore.getPlugin(PluginId.getId('Git4Idea')).getPluginClassLoader()
        var settingsClass = java.lang.Class.forName('git4idea.config.GitVcsApplicationSettings', true, gitLoader)
        settingsClass.getMethod('getInstance').invoke(null).setStagingAreaEnabled($enabled)
        // invokeLater runs with the write-intent lock a settings apply has; the listeners need it.
        ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
            run: function () {
                ApplicationManager.getApplication().getMessageBus().syncPublisher(CommitModeManager.SETTINGS).settingsChanged()
            }
        }))
        """,
        true,
    )
}

/** The Commit view's changelist tree, shown while the staging area is off. */
fun RemoteRobot.changesTree(): JTreeFixture =
    find(JTreeFixture::class.java, byXpath("//div[@class='LocalChangesListView' or @class='ChangesListView']"), Duration.ofSeconds(30))

/** The Git staging area's changes tree, once the Commit tool window shows it with its rows built. */
fun RemoteRobot.stagingTree(): JTreeFixture {
    val tree = find(JTreeFixture::class.java, byXpath("//div[@class='MyChangesTree']"), Duration.ofSeconds(30))
    waitFor(Duration.ofSeconds(30), Duration.ofMillis(250), "the staging tree to list its changes") {
        tree.collectRows().any { it.startsWith("Staged") }
    }
    return tree
}

/**
 * Saves a desktop screenshot of the sandbox IDE's windows (frame plus open popups and dialogs) to
 * `build/reports/ui-test/review/[name].png`, for a person to review.
 *
 * Uses `java.awt.Robot`, so it shows exactly what is on screen. Run the sandbox on Xvfb with
 * `WAYLAND_DISPLAY` unset: on a Wayland display the capture blocks forever.
 */
fun RemoteRobot.saveReviewScreenshot(name: String) {
    val bytes = callJs<ByteArray>(
        """
        importClass(java.awt.Toolkit)
        importClass(java.awt.Rectangle)
        importClass(java.awt.Window)
        importPackage(java.io)
        importPackage(javax.imageio)

        var bounds = null
        var windows = Window.getWindows()
        for (var w = 0; w < windows.length; w++) {
            var target = windows[w]
            if (!target.isShowing() || target.getWidth() <= 0 || target.getHeight() <= 0) continue
            try {
                var at = target.getLocationOnScreen()
                var rect = new Rectangle(at.x, at.y, target.getWidth(), target.getHeight())
                bounds = (bounds == null) ? rect : bounds.union(rect)
            } catch (ignored) { }
        }
        if (bounds == null) {
            var screen = Toolkit.getDefaultToolkit().getScreenSize()
            bounds = new Rectangle(0, 0, screen.width, screen.height)
        }
        // Fully qualified: robot-server already binds `robot` and imports another `Robot` class.
        var awtRobot = new java.awt.Robot()
        var image = awtRobot.createScreenCapture(bounds)
        var baos = new ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        baos.toByteArray()
        """,
        false,
    )
    File("build/reports/ui-test/review").apply { mkdirs() }.resolve("$name.png").writeBytes(bytes)
}
