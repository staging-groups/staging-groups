package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JCheckboxFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

/**
 * The colour the editor tab of [fileName] shows, as an RGB integer string, `none` without one, or
 * `no tab`. Read from the tab's `TabInfo`, which caches the colour until something asks for an
 * update: asking the providers would say what a repaint would show, not what the tab shows.
 */
fun RemoteRobot.tabColour(fileName: String): String = callJs(
    """
    importClass(java.awt.Window)
    importClass(com.intellij.openapi.fileEditor.impl.EditorTabLabel)
    function find(c) {
        if (c instanceof EditorTabLabel && c.isShowing()) {
            var name = c.getAccessibleContext() == null ? null : c.getAccessibleContext().getAccessibleName()
            if (name == '$fileName') return c
        }
        var children = c.getComponents ? c.getComponents() : []
        for (var i = 0; i < children.length; i++) { var found = find(children[i]); if (found != null) return found }
        return null
    }
    var label = null
    var windows = Window.getWindows()
    for (var w = 0; w < windows.length && label == null; w++) if (windows[w].isShowing()) label = find(windows[w])
    if (label == null) 'no tab'
    else { var colour = label.getInfo().getTabColor(); colour == null ? 'none' : '' + colour.getRGB() }
    """,
    true,
)

/**
 * The file colour the tree paints behind [row], as an RGB integer string, or `none`. Read through
 * the paint path, which also checks the IDE's own File Colors switches.
 */
fun JTreeFixture.rowFileColour(row: Int): String = callJs(
    "var colour = component.getPathBackground(component.getPathForRow($row), $row); colour == null ? 'none' : '' + colour.getRGB()",
    true,
)

/** The current theme's colour for the File Colors ID [colourId], as an RGB integer string. */
fun RemoteRobot.fileColour(colourId: String): String = callJs(
    """
    importClass(com.intellij.openapi.project.ProjectManager)
    importClass(com.intellij.ui.FileColorManager)
    '' + FileColorManager.getInstance(ProjectManager.getInstance().getOpenProjects()[0]).getColor('$colourId').getRGB()
    """,
    true,
)

/**
 * Ticks or unticks **Enable file colors** in **Settings | Appearance & Behavior | File Colors** and
 * clicks OK, as a user does: the settings page is what repaints the views afterwards.
 */
fun RemoteRobot.setFileColoursEnabled(enabled: Boolean) {
    runJs(
        """
        importClass(com.intellij.openapi.project.ProjectManager)
        importClass(com.intellij.openapi.application.ApplicationManager)
        importClass(com.intellij.openapi.options.ShowSettingsUtil)
        var project = ProjectManager.getInstance().getOpenProjects()[0]
        ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
            run: function () { ShowSettingsUtil.getInstance().showSettingsDialog(project, 'File Colors') }
        }))
        """,
        false,
    )
    val settings = find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog' and @title='Settings']"), Duration.ofSeconds(10))
    val checkBox = settings.find(JCheckboxFixture::class.java, byXpath("//div[@class='JBCheckBox' and @accessiblename='Enable file colors']"), Duration.ofSeconds(10))
    checkBox.setValue(enabled)
    settings.button("OK").click()
}

/** Whether the IDE's own **Enable file colors** is on. */
fun RemoteRobot.fileColoursEnabled(): Boolean = callJs(
    """
    importClass(com.intellij.openapi.project.ProjectManager)
    importClass(com.intellij.ui.FileColorManager)
    FileColorManager.getInstance(ProjectManager.getInstance().getOpenProjects()[0]).isEnabled()
    """,
    true,
)
