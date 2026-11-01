package dev.yendrrek.staginggroups.lifecycle

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DynamicUnloadListenerTest : BasePlatformTestCase() {

    /** A mismatch is silent: the listener would hear every other plugin's unload and never its own. */
    fun `test listens for this plugin's own id`() {
        assertEquals("Staging Groups", PluginManagerCore.getPlugin(DynamicUnloadListener.PLUGIN_ID)?.name)
    }
}
