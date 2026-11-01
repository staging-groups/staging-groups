package dev.yendrrek.staginggroups.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.state.StagingGroupsListener
import dev.yendrrek.staginggroups.state.StagingGroupsStore
import dev.yendrrek.staginggroups.vcs.ChangedPaths
import dev.yendrrek.staginggroups.vcs.VcsChangesView
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * The **Staging Group** menu acting on a selection in a VCS changes view, such as the staging area,
 * and on files and folders selected elsewhere, such as in the Project view.
 */
class StagingGroupActionsTest : BasePlatformTestCase() {

    private val foo = "/project/src/Foo.kt"
    private val bar = "/project/src/Bar.kt"

    private val store get() = StagingGroupsStore.getInstance(project)

    private val changesView = FakeChangesView()

    override fun setUp() {
        super.setUp()
        ExtensionTestUtil.maskExtensions(VcsChangesView.EP_NAME, listOf(changesView), testRootDisposable)
        // The light project is shared by every test class: start from two empty groups.
        store.update {
            val withA = GroupsCommands.create(GroupsState(), "Feature A", "", "a")
            GroupsCommands.create(withA, "auth_flow", "", "r")
        }
    }

    override fun tearDown() {
        try {
            TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
            store.update { GroupsState() }
        } finally {
            super.tearDown()
        }
    }

    fun `test menu is hidden without a selection and shown with one`() {
        val menu = action("StagingGroups.Assign")

        assertFalse(updated(menu, selecting()).presentation.isVisible)
        assertTrue(updated(menu, selecting(foo)).presentation.isVisible)
    }

    fun `test menu lists every group by its literal name, then New, Remove, Remove Unchanged and Manage`() {
        val menu = action("StagingGroups.Assign") as StagingGroupMenu
        val choices = action("StagingGroups.Assign.Choices") as StagingGroupChoices
        val event = selecting(foo)

        val names = choices.getChildren(event).map { updated(it, event).presentation.text }

        assertEquals(listOf("Feature A", "auth_flow"), names)
        assertEquals(
            listOf("StagingGroups.Assign.Choices", null, "StagingGroups.NewGroup", "StagingGroups.Remove", null, "StagingGroups.RemoveUnchanged", "StagingGroups.Manage"),
            menu.getChildren(event).map { ActionManager.getInstance().getId(it) },
        )
    }

    fun `test menu says there are no staging groups, disabled, until one exists`() {
        store.update { GroupsState() }
        val choices = action("StagingGroups.Assign.Choices") as StagingGroupChoices
        val event = selecting(foo)

        val entries = choices.getChildren(event).map { updated(it, event).presentation }

        assertEquals(listOf("No Staging Groups"), entries.map { it.text })
        assertTrue(entries.single().isVisible)
        assertFalse(entries.single().isEnabled)
    }

    fun `test assigning moves every selected file, counting a file listed twice once, and ticks the group`() {
        store.update { GroupsCommands.assign(it, listOf(bar), "r") }
        val featureA = assignAction("a")

        // A file both staged and unstaged appears twice in the staging tree's selection.
        featureA.actionPerformed(selecting(foo, bar, foo))

        assertEquals(mapOf(foo to "a", bar to "a"), store.state.assignments)
        assertTrue(Toggleable.isSelected(updated(featureA, selecting(foo, bar)).presentation))
        assertFalse(Toggleable.isSelected(updated(assignAction("r"), selecting(foo, bar)).presentation))
    }

    fun `test a group is ticked only when every selected file is in it`() {
        store.update { GroupsCommands.assign(it, listOf(foo), "a") }

        assertTrue(Toggleable.isSelected(updated(assignAction("a"), selecting(foo)).presentation))
        assertFalse(Toggleable.isSelected(updated(assignAction("a"), selecting(foo, bar)).presentation))
    }

    fun `test remove is enabled only when a selected file is in a group, and removes just those`() {
        val remove = action("StagingGroups.Remove")
        assertFalse(updated(remove, selecting(foo)).presentation.isEnabled)

        store.update { GroupsCommands.assign(it, listOf(foo, bar), "a") }
        assertTrue(updated(remove, selecting(foo)).presentation.isEnabled)

        remove.actionPerformed(selecting(foo))
        assertEquals(mapOf(bar to "a"), store.state.assignments)
    }

    fun `test new staging group creates the group with the selected files in one change`() {
        var announced = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            StagingGroupsListener.TOPIC,
            object : StagingGroupsListener {
                override fun groupsChanged() {
                    announced++
                }
            },
        )
        answerNameDialog("  Docs  ")

        action("StagingGroups.NewGroup").actionPerformed(selecting(foo, bar))

        val docs = store.snapshot().groups.last()
        assertEquals("Docs", docs.name)
        assertEquals(2, docs.order)
        assertEquals(setOf(foo, bar), store.snapshot().pathsIn(docs.id))
        assertEquals(1, announced)
    }

    fun `test cancelling new staging group changes nothing`() {
        answerNameDialog(null)

        action("StagingGroups.NewGroup").actionPerformed(selecting(foo))

        assertEquals(2, store.snapshot().groups.size)
        assertTrue(store.state.assignments.isEmpty())
    }

    fun `test name validator rejects blank and duplicate names, reporting only duplicates by their existing spelling`() {
        val validator = NameValidator { store.state }

        assertFalse(validator.checkInput("   "))
        assertNull(validator.getErrorText("   "))
        assertFalse(validator.checkInput(" feature a "))
        assertEquals("A staging group named 'Feature A' already exists", validator.getErrorText(" feature a "))
        assertTrue(validator.checkInput("Feature B"))
        assertNull(validator.getErrorText("Feature B"))
    }

    fun `test a folder selected outside the changes view stands for the changed files under it`() {
        val root = createTempDirectory("staging-groups-test")
        try {
            val folder = root.resolve("src").createDirectories()
            folder.resolve("Changed.kt").writeText("")
            folder.resolve("Unchanged.kt").writeText("")
            val folderFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(folder)!!
            val changed = "${folderFile.path}/Changed.kt"
            changesView.changed = setOf(changed, "${folderFile.path}gen/Elsewhere.kt")
            val event = TestActionEvent.createTestEvent(
                SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(folderFile))
                    .build(),
            )

            assignAction("a").actionPerformed(event)

            assertEquals(mapOf(changed to "a"), store.state.assignments)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    fun `test remove unchanged files keeps only files the changes view lists`() {
        val unchanged = "/project/src/Committed.kt"
        store.update { GroupsCommands.assign(GroupsCommands.assign(it, listOf(foo, unchanged), "a"), listOf(bar), "r") }
        changesView.changed = setOf(foo, bar, "/project/src/Ungrouped.kt")
        val removeUnchanged = action("StagingGroups.RemoveUnchanged")
        assertTrue(updated(removeUnchanged, selecting(foo)).presentation.isEnabled)

        removeUnchanged.actionPerformed(selecting(foo))
        PlatformTestUtil.waitWithEventsDispatching("the unchanged file to leave its group", { unchanged !in store.state.assignments }, 10)

        assertEquals(mapOf(foo to "a", bar to "r"), store.state.assignments)
        assertEquals(listOf("Feature A", "auth_flow"), store.snapshot().groups.map { it.name })
        assertFalse(updated(removeUnchanged, selecting(foo)).presentation.isEnabled)
    }

    fun `test remove unchanged files is disabled and removes nothing until the changes view has loaded`() {
        store.update { GroupsCommands.assign(it, listOf(foo, bar), "a") }
        changesView.changed = emptySet()
        changesView.loaded = false
        val removeUnchanged = action("StagingGroups.RemoveUnchanged")

        assertFalse(updated(removeUnchanged, selecting(foo)).presentation.isEnabled)
        removeUnchanged.actionPerformed(selecting(foo))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(mapOf(foo to "a", bar to "a"), store.state.assignments)
    }

    private fun action(id: String): AnAction = ActionManager.getInstance().getAction(id)

    private fun assignAction(groupId: String) = AssignToStagingGroupAction(groupId)

    /** An event from the changes view with [paths] selected. */
    private fun selecting(vararg paths: String): AnActionEvent {
        changesView.selection = paths.map { SelectedPath(it, isDirectory = false) }
        return TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    }

    private fun updated(action: AnAction, event: AnActionEvent): AnActionEvent {
        val fresh = TestActionEvent.createTestEvent(action, event.dataContext)
        action.update(fresh)
        return fresh
    }

    /**
     * A changes view whose selection and changed files the test sets; `null` stands for "not shown".
     * [loaded] is whether it has read every repository.
     */
    private class FakeChangesView : VcsChangesView {
        var selection: List<SelectedPath>? = null
        var changed: Set<String>? = null
        var loaded = true

        override fun changedPaths(project: Project): ChangedPaths? = changed?.let { ChangedPaths(it, loaded) }

        override fun selectedPaths(dataContext: DataContext): List<SelectedPath>? = selection
    }

    /** Answers the next input dialogs with [name], validated as the real dialog would; `null` cancels. */
    private fun answerNameDialog(name: String?) {
        TestDialogManager.setTestInputDialog(
            object : TestInputDialog {
                override fun show(message: String): String? = show(message, null)

                override fun show(message: String, validator: InputValidator?): String? =
                    name?.takeIf { validator == null || validator.canClose(it) }
            },
        )
    }
}
