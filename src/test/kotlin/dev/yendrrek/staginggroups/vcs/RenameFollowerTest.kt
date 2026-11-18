package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** [RenameFollower] against a fake changes view whose renames the test sets. */
class RenameFollowerTest : BasePlatformTestCase() {

    private val readme = Rename("/p/README.md", "/p/NOTES.md")
    private val util = Rename("/p/Util.kt", "/p/Helpers.kt")

    private val store get() = StagingGroupsStore.getInstance(project)
    private val changesView = FakeChangesView()
    private lateinit var scope: CoroutineScope
    private lateinit var follower: RenameFollower

    override fun setUp() {
        super.setUp()
        ExtensionTestUtil.maskExtensions(VcsChangesView.EP_NAME, listOf(changesView), testRootDisposable)
        // The light project is shared by every test class: start from one group and a new follower.
        store.update {
            val group = GroupsCommands.create(GroupsState(), "Refactor", "r")
            GroupsCommands.assign(group, listOf(readme.oldPath, util.oldPath), "r")
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        follower = RenameFollower(project, scope)
        project.replaceService(RenameFollower::class.java, follower, testRootDisposable)
    }

    override fun tearDown() {
        try {
            scope.cancel()
            Disposer.dispose(follower)
            store.update { GroupsState() }
        } finally {
            super.tearDown()
        }
    }

    fun `test a rename the view starts listing puts the new path in the old path's group`() {
        changesView.list(readme)

        waitForAssignments(readme.oldPath, util.oldPath, readme.newPath)
    }

    fun `test a new path taken out of its group stays out while the view lists the rename`() {
        changesView.list(readme)
        waitForAssignments(readme.oldPath, util.oldPath, readme.newPath)
        store.update { GroupsCommands.unassign(it, listOf(readme.newPath)) }

        changesView.list(readme, util)

        waitForAssignments(readme.oldPath, util.oldPath, util.newPath)
    }

    fun `test a changelist update makes the follower read the view again`() {
        changesView.list(readme, notify = false)

        project.messageBus.syncPublisher(ChangeListListener.TOPIC).changeListUpdateDone()

        waitForAssignments(readme.oldPath, util.oldPath, readme.newPath)
    }

    private fun waitForAssignments(vararg paths: String) {
        val expected = paths.toSet()
        PlatformTestUtil.waitWithEventsDispatching(
            { "the assignments to become $expected, not ${store.state.assignments.keys}" },
            { store.state.assignments.keys == expected },
            10,
        )
    }

    /** A shown, loaded changes view listing the renames the test sets. */
    private class FakeChangesView : VcsChangesView {
        @Volatile
        private var renames: Set<Rename> = emptySet()
        private val listeners = mutableListOf<Runnable>()

        /** Lists [renames] and, if [notify], tells the listeners, as the view does after an update. */
        fun list(vararg renames: Rename, notify: Boolean = true) {
            this.renames = renames.toSet()
            if (notify) listeners.forEach(Runnable::run)
        }

        override fun changedPaths(project: Project): ChangedPaths =
            ChangedPaths(renames.mapTo(HashSet()) { it.newPath }, renames, isLoaded = true)

        override fun addChangeListener(project: Project, listener: Runnable, parentDisposable: Disposable) {
            listeners += listener
        }

        override fun selectedPaths(dataContext: DataContext): List<SelectedPath>? = null
    }
}
