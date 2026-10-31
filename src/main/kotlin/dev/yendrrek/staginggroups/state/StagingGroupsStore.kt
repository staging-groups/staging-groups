package dev.yendrrek.staginggroups.state

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.yendrrek.staginggroups.domain.GroupsSnapshot
import dev.yendrrek.staginggroups.domain.GroupsState

/**
 * A project's staging groups, stored in the workspace file so they stay out of version control.
 *
 * Every change goes through [update] and is announced on [StagingGroupsListener.TOPIC].
 */
@Service(Service.Level.PROJECT)
@State(name = "StagingGroups", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class StagingGroupsStore(private val project: Project) :
    SerializablePersistentStateComponent<GroupsState>(GroupsState()) {

    @Volatile
    private var cachedSnapshot: GroupsSnapshot = GroupsSnapshot.EMPTY

    /** The current groups; cheap to call often and from any thread. */
    fun snapshot(): GroupsSnapshot {
        val current = state
        val cached = cachedSnapshot
        if (cached.state === current) return cached
        return GroupsSnapshot(current).also { cachedSnapshot = it }
    }

    /** Applies [change] atomically and announces it, unless it changed nothing. */
    fun update(change: (GroupsState) -> GroupsState) {
        val before = state
        val after = updateState(change)
        if (after != before) publishChange()
    }

    private fun publishChange() {
        if (!project.isDisposed) project.messageBus.syncPublisher(StagingGroupsListener.TOPIC).groupsChanged()
    }

    companion object {
        fun getInstance(project: Project): StagingGroupsStore = project.service()
    }
}
