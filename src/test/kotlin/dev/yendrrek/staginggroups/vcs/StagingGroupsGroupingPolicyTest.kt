package dev.yendrrek.staginggroups.vcs

import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.state.StagingGroupsStore

/** The policy builds the expected tree through the platform's own grouping machinery. */
class StagingGroupsGroupingPolicyTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light project is shared by every test class: start from empty state.
        StagingGroupsStore.getInstance(project).update { _ ->
            GroupsState()
                .let { GroupsCommands.create(it, "Refactor", "", id = "r") }
                .let { GroupsCommands.create(it, "Feature A", "", id = "a") }
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

    fun `test the toggle action uses the policy's key`() {
        assertEquals(StagingGroupsGroupingPolicy.KEY, ToggleStagingGroupsGroupingAction().groupingKey)
        assertNotNull(ChangesGroupingSupport.findFactory(StagingGroupsGroupingPolicy.KEY))
    }

    private fun model(vararg keys: String): ChangesBrowserNode<*> {
        val support = ChangesGroupingSupport(project, this, false)
        support.setGroupingKeys(keys.toSet())
        val paths = files.map { VcsUtil.getFilePath(it, false) }
        return TreeModelBuilder.buildFromFilePaths(project, support.grouping, paths).root as ChangesBrowserNode<*>
    }

    private fun tree(vararg keys: String): String = buildString {
        fun walk(node: ChangesBrowserNode<*>, depth: Int) {
            node.iterateNodeChildren().forEach { child ->
                appendLine("  ".repeat(depth) + child.textPresentation)
                walk(child, depth + 1)
            }
        }
        walk(model(*keys), 0)
    }.trimEnd()
}
