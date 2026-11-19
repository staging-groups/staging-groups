package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.ChangeListRemoteState
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeListNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.SelectedPath
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** The policy builds the expected tree through the platform's own grouping machinery. */
class StagingGroupsGroupingPolicyTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light project is shared by every test class: start from empty state.
        StagingGroupsStore.getInstance(project).update { _ ->
            GroupsState()
                .let { GroupsCommands.create(it, "Refactor", id = "r") }
                .let { GroupsCommands.create(it, "Feature A", id = "a") }
                .let { GroupsCommands.assign(it, listOf("/p/src/Foo.kt", "/p/src/util/Bar.kt"), "a") }
                .let { GroupsCommands.assign(it, listOf("/p/src/util/Util.kt"), "r") }
        }
    }

    private val files = listOf("/p/src/Foo.kt", "/p/src/util/Bar.kt", "/p/src/util/Util.kt", "/p/README.md", "/p/src/Other.kt")

    fun `test groups come first in their own order and ungrouped files follow`() {
        assertEquals(
            """
            Refactor
              /p/src/util/Util.kt
            Feature A
              /p/src/util/Bar.kt
              /p/src/Foo.kt
            /p/src/Other.kt
            /p/README.md
            """.trimIndent(),
            tree(StagingGroupsGroupingPolicy.KEY),
        )
    }

    fun `test directory grouping nests inside each group`() {
        assertEquals(
            """
            Refactor
              /p/src/util
                Util.kt
            Feature A
              /p/src
                util
                  Bar.kt
                Foo.kt
            /p
              src
                Other.kt
              README.md
            """.trimIndent(),
            tree(StagingGroupsGroupingPolicy.KEY, ChangesGroupingSupport.DIRECTORY_GROUPING),
        )
    }

    fun `test group nodes are typed, ordered and not counted as files`() {
        val root = model(StagingGroupsGroupingPolicy.KEY)
        val groupNodes = root.iterateNodeChildren().filter(StagingGroupNode::class.java).toList()

        assertEquals(listOf("Refactor", "Feature A"), groupNodes.map { it.group.name })
        assertFalse(groupNodes.first().isMeaningfulNode)
        assertEquals(5, root.fileCount)
    }

    fun `test a section that lists every group shows a group with no files there`() {
        StagingGroupsStore.getInstance(project).update { GroupsCommands.create(it, "Docs", id = "d") }
        val unstaged = sectionTree(listsEveryGroup = true, "/p/src/util/Util.kt", "/p/README.md")

        assertEquals(
            """
            Unstaged
              Refactor
                /p/src/util/Util.kt
              Feature A
              Docs
              /p/README.md
            """.trimIndent(),
            unstaged,
        )
    }

    fun `test other sections show only groups with files`() {
        StagingGroupsStore.getInstance(project).update { GroupsCommands.create(it, "Docs", id = "d") }

        assertEquals(
            """
            Staged
              Refactor
                /p/src/util/Util.kt
              /p/README.md
            """.trimIndent(),
            sectionTree(listsEveryGroup = false, "/p/src/util/Util.kt", "/p/README.md"),
        )
    }

    fun `test the active changelist lists every group`() {
        val active = ChangeListManager.getInstance(project).defaultChangeList
        assertTrue(active.isDefault)
        val section = ChangesBrowserChangeListNode(project, active, ChangeListRemoteState())
        val builder = TreeModelBuilder(project, grouping(StagingGroupsGroupingPolicy.KEY))
        builder.insertSubtreeRoot(section)
        builder.setFilePaths(listOf(VcsUtil.getFilePath("/p/README.md", false)), section)
        builder.build()

        assertEquals(listOf("Refactor", "Feature A", "/p/README.md"), section.iterateNodeChildren().map { it.getTextPresentation() }.toList())
    }

    fun `test the toggle action uses the policy's key`() {
        assertEquals(StagingGroupsGroupingPolicy.KEY, ToggleStagingGroupsGroupingAction().groupingKey)
        assertNotNull(ChangesGroupingSupport.findFactory(StagingGroupsGroupingPolicy.KEY))
    }

    private fun grouping(vararg keys: String): ChangesGroupingPolicyFactory {
        val support = ChangesGroupingSupport(project, this, false)
        support.setGroupingKeys(keys.toSet())
        return support.grouping
    }

    private fun model(vararg keys: String): ChangesBrowserNode<*> {
        val paths = files.map { VcsUtil.getFilePath(it, false) }
        return TreeModelBuilder.buildFromFilePaths(project, grouping(*keys), paths).root as ChangesBrowserNode<*>
    }

    private fun tree(vararg keys: String): String = render(model(*keys))

    /**
     * The tree of [paths] under one section node, grouped by staging group, where the changes view
     * says whether that section [listsEveryGroup]. The section is named as the staging area's would be.
     */
    private fun sectionTree(listsEveryGroup: Boolean, vararg paths: String): String {
        val builder = TreeModelBuilder(project, grouping(StagingGroupsGroupingPolicy.KEY))
        val section = builder.createTagNode(if (listsEveryGroup) "Unstaged" else "Staged")
        ExtensionTestUtil.maskExtensions(VcsChangesView.EP_NAME, listOf(SectionView(section, listsEveryGroup)), testRootDisposable)
        builder.setFilePaths(paths.map { VcsUtil.getFilePath(it, false) }, section)
        return render(builder.build().root as ChangesBrowserNode<*>)
    }

    private fun render(root: ChangesBrowserNode<*>): String = buildString {
        fun walk(node: ChangesBrowserNode<*>, depth: Int) {
            node.iterateNodeChildren().forEach { child ->
                appendLine("  ".repeat(depth) + child.textPresentation)
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
    }.trimEnd()

    /** A changes view whose [section] node lists every group, or not. */
    private class SectionView(private val section: ChangesBrowserNode<*>, private val listsEveryGroup: Boolean) : VcsChangesView {
        override fun changedPaths(project: Project): ChangedPaths? = null
        override fun addChangeListener(project: Project, listener: Runnable, parentDisposable: Disposable) = Unit
        override fun selectedPaths(dataContext: DataContext): List<SelectedPath>? = null
        override fun listsEveryGroup(section: ChangesBrowserNode<*>): Boolean = listsEveryGroup && section === this.section
    }
}
