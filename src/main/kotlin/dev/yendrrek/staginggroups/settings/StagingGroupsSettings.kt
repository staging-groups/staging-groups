package dev.yendrrek.staginggroups.settings

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import dev.yendrrek.staginggroups.state.StagingGroupsListener

/**
 * Persisted Staging Groups settings. [colourTabs] and [colourTrees] apply only while [useColours]
 * is on. [autoCleanup] is **Remove committed files from staging groups**.
 *
 * Persisted: never rename this class or its properties, or change their XML names. `@OptionTag` is
 * what makes the platform's serialiser store these final fields.
 */
data class SettingsState(
    @field:OptionTag("useColours") @JvmField val useColours: Boolean = false,
    @field:OptionTag("colourTabs") @JvmField val colourTabs: Boolean = true,
    @field:OptionTag("colourTrees") @JvmField val colourTrees: Boolean = true,
    @field:OptionTag("autoCleanup") @JvmField val autoCleanup: Boolean = false,
)

/** A project's Staging Groups settings. Every change is announced on [StagingGroupsListener.TOPIC]. */
@Service(Service.Level.PROJECT)
@State(name = "StagingGroupsSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class StagingGroupsSettings(private val project: Project) :
    SerializablePersistentStateComponent<SettingsState>(SettingsState()) {

    /** The current settings; cheap to call often and from any thread. */
    fun snapshot(): SettingsState = state

    /** Whether grouped files are tinted in editor tabs. */
    val colourEditorTabs: Boolean get() = state.let { it.useColours && it.colourTabs }

    /** Whether grouped files are tinted in the Project view and the Commit and staging trees. */
    val colourFileTrees: Boolean get() = state.let { it.useColours && it.colourTrees }

    /** Applies [change] atomically and announces it, unless it changed nothing. */
    fun update(change: (SettingsState) -> SettingsState) {
        val before = state
        val after = updateState(change)
        if (after != before) publishChange()
    }

    private fun publishChange() {
        if (!project.isDisposed) project.messageBus.syncPublisher(StagingGroupsListener.TOPIC).settingsChanged()
    }

    companion object {
        fun getInstance(project: Project): StagingGroupsSettings = project.service()
    }
}
