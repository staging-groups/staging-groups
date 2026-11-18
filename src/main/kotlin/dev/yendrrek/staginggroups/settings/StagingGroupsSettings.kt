package dev.yendrrek.staginggroups.settings

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag

/**
 * Persisted Staging Groups settings. [autoCleanup] is **Remove committed files from staging groups**.
 *
 * Persisted: never rename this class or its properties, or change their XML names. `@OptionTag` is
 * what makes the platform's serialiser store these final fields.
 */
data class SettingsState(
    @field:OptionTag("autoCleanup") @JvmField val autoCleanup: Boolean = false,
)

/** A project's Staging Groups settings. */
@Service(Service.Level.PROJECT)
@State(name = "StagingGroupsSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class StagingGroupsSettings : SerializablePersistentStateComponent<SettingsState>(SettingsState()) {

    /** The current settings; cheap to call often and from any thread. */
    fun snapshot(): SettingsState = state

    /** Applies [change] atomically. */
    fun update(change: (SettingsState) -> SettingsState) {
        updateState(change)
    }

    companion object {
        fun getInstance(project: Project): StagingGroupsSettings = project.service()
    }
}
