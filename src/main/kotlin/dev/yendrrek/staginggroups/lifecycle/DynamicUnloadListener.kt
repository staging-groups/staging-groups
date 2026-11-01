package dev.yendrrek.staginggroups.lifecycle

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import dev.yendrrek.staginggroups.ui.FileColourRepaint
import dev.yendrrek.staginggroups.vcs.StagingGroupsGroupingRelease
import java.awt.Toolkit

/**
 * Enabling or disabling the plugin without a restart. On load it colours the files already open and
 * groups the changes trees by staging group again. On unload it takes the grouping and every colour
 * back, and drops the clipboard's references to the plugin, so its class loader can go. The platform
 * saves the plugin's state and registers and drops its extensions itself, but repaints and rebuilds
 * nothing.
 */
class DynamicUnloadListener : DynamicPluginListener {

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId != PLUGIN_ID) return
        // Caught: enabling must not fail over files that could not be repainted.
        try {
            openProjects().forEach(FileColourRepaint::request)
        } catch (e: Exception) {
            thisLogger().warn("Could not colour the open files on load", e)
        }
        try {
            StagingGroupsGroupingRelease.restore()
        } catch (e: Exception) {
            thisLogger().warn("Could not group the changes trees by staging group again on load", e)
        }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId != PLUGIN_ID) return
        // Each step caught on its own: one failing must not stop the others, nor the unload.
        try {
            StagingGroupsGroupingRelease.release()
        } catch (e: Exception) {
            thisLogger().warn("Could not take the staging groups out of the changes trees on unload", e)
        }
        try {
            Unloading.start()
            openProjects().forEach(FileColourRepaint::request)
        } catch (e: Exception) {
            thisLogger().warn("Could not clear the file colours on unload", e)
        }
        try {
            ClipboardRelease.release(Toolkit.getDefaultToolkit().systemClipboard)
        } catch (e: Exception) {
            thisLogger().warn("Could not release the system clipboard on unload", e)
        }
        try {
            Toolkit.getDefaultToolkit().systemSelection?.let(ClipboardRelease::release)
        } catch (e: Exception) {
            thisLogger().warn("Could not release the primary selection on unload", e)
        }
    }

    private fun openProjects(): List<Project> =
        ProjectManager.getInstanceIfCreated()?.openProjects?.filterNot { it.isDisposed }.orEmpty()

    companion object {
        /** The `<id>` in plugin.xml. */
        val PLUGIN_ID: PluginId = PluginId.getId("dev.yendrrek.staginggroups")
    }
}
