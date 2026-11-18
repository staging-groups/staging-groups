package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.settings.SettingsState
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** **Remove committed files from staging groups** around a commit, against a fake changes view. */
class RemoveCommittedFilesCheckinHandlerTest : BasePlatformTestCase() {

    private val committed = "/p/Committed.kt"
    private val partlyCommitted = "/p/PartlyCommitted.kt"
    private val notCommitted = "/p/NotCommitted.kt"
    private val assignedAhead = "/p/AssignedAhead.kt"

    private val store get() = StagingGroupsStore.getInstance(project)
    private val settings get() = StagingGroupsSettings.getInstance(project)
    private val changesView = FakeChangesView()

    override fun setUp() {
        super.setUp()
        ExtensionTestUtil.maskExtensions(VcsChangesView.EP_NAME, listOf(changesView), testRootDisposable)
        // The light project is shared by every test class: start from one group.
        store.update {
            val group = GroupsCommands.create(GroupsState(), "Feature A", "a")
            GroupsCommands.assign(group, listOf(committed, partlyCommitted, notCommitted, assignedAhead), "a")
        }
        changesView.changed = ChangedPaths(setOf(committed, partlyCommitted, notCommitted, "/p/Ungrouped.kt"), emptySet(), isLoaded = true)
    }

    override fun tearDown() {
        try {
            store.update { GroupsState() }
            settings.update { SettingsState() }
        } finally {
            super.tearDown()
        }
    }

    fun `test a commit takes out only the files it left without changes`() {
        settings.update { it.copy(autoCleanup = true) }

        commit(changedAfter = setOf(partlyCommitted, notCommitted))

        assertEquals(setOf(partlyCommitted, notCommitted, assignedAhead), store.state.assignments.keys)
    }

    fun `test nothing changes with the setting off`() {
        val before = store.state

        commit(changedAfter = setOf(notCommitted))

        assertSame(before, store.state)
    }

    fun `test nothing changes while the changes view is still loading`() {
        settings.update { it.copy(autoCleanup = true) }
        changesView.changed = ChangedPaths(emptySet(), emptySet(), isLoaded = false)
        val before = store.state

        commit(changedAfter = emptySet())

        assertSame(before, store.state)
    }

    fun `test a failed commit changes nothing`() {
        settings.update { it.copy(autoCleanup = true) }
        val before = store.state
        val handler = RemoveCommittedFilesCheckinHandler(project)

        assertEquals(CheckinHandler.ReturnResult.COMMIT, handler.beforeCheckin())
        changesView.changed = ChangedPaths(emptySet(), emptySet(), isLoaded = true)
        handler.checkinFailed(emptyList())
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertSame(before, store.state)
    }

    /** Runs a successful commit after which the view lists [changedAfter], and waits for the handler. */
    private fun commit(changedAfter: Set<String>) {
        val handler = RemoveCommittedFilesCheckinHandler(project)
        assertEquals(CheckinHandler.ReturnResult.COMMIT, handler.beforeCheckin())
        changesView.changed = ChangedPaths(changedAfter, emptySet(), isLoaded = changesView.changed?.isLoaded ?: true)
        handler.checkinSuccessful()
        // The handler acts after the change list's update; a callback queued behind its own runs after it.
        var handled = false
        ChangeListManager.getInstance(project).invokeAfterUpdate(true) { handled = true }
        PlatformTestUtil.waitWithEventsDispatching("the handler to run after the change list's update", { handled }, 10)
    }

    private class FakeChangesView : VcsChangesView {
        var changed: ChangedPaths? = null

        override fun changedPaths(project: Project): ChangedPaths? = changed

        override fun addChangeListener(project: Project, listener: Runnable, parentDisposable: Disposable) = Unit

        override fun selectedPaths(dataContext: DataContext): List<SelectedPath>? = null
    }
}
