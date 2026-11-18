package dev.yendrrek.staginggroups.state

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import dev.yendrrek.staginggroups.domain.GroupsCommands
import dev.yendrrek.staginggroups.domain.GroupsState
import dev.yendrrek.staginggroups.domain.StagingGroup
import dev.yendrrek.staginggroups.settings.SettingsState
import dev.yendrrek.staginggroups.settings.StagingGroupsSettings

/**
 * Persisted state survives the platform's XML serialisation, and the saved format stays readable:
 * the files under `saved-state/` are what earlier versions wrote, so a rename of a persisted class
 * or property fails here instead of losing users' groups.
 */
class PersistenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light project is shared by every test class: start from empty state.
        StagingGroupsStore.getInstance(project).update { GroupsState() }
        StagingGroupsSettings.getInstance(project).update { SettingsState() }
    }

    private val sample = GroupsState(
        groups = listOf(
            StagingGroup(id = "a", name = "Feature A", order = 0),
            StagingGroup(id = "r", name = "auth_flow & <more>", order = 1),
        ),
        assignments = linkedMapOf(
            "/home/user/project/src/Foo.kt" to "a",
            "/home/user/project/My Dir/Zażółć.kt" to "r",
            "C:/work/project/Bar.kt" to "a",
        ),
    )

    fun `test groups survive serialisation through the store`() {
        val saved = StagingGroupsStore(project).apply { update { sample } }.state
        val element = XmlSerializer.serialize(saved)

        val restored = StagingGroupsStore(project)
        restored.loadState(XmlSerializer.deserialize(element, GroupsState::class.java))

        assertEquals(sample, restored.state)
        assertEquals(sample.groups[1], restored.snapshot().groupFor("/home/user/project/My Dir/Zażółć.kt"))
    }

    fun `test default groups state serialises to nothing and reads back as empty`() {
        val element = XmlSerializer.serialize(GroupsState())

        assertEquals(GroupsState(), XmlSerializer.deserialize(element, GroupsState::class.java))
    }

    fun `test saved groups file reads back`() {
        val element = JDOMUtil.load(javaClass.getResourceAsStream("/saved-state/StagingGroups.xml")!!)

        assertEquals(sample, XmlSerializer.deserialize(element, GroupsState::class.java))
    }

    fun `test settings survive serialisation and default to clean-up off`() {
        assertEquals(SettingsState(autoCleanup = false), SettingsState())

        val changed = SettingsState(autoCleanup = true)
        val element = XmlSerializer.serialize(changed)

        assertEquals(changed, XmlSerializer.deserialize(element, SettingsState::class.java))
    }

    fun `test saved settings file reads back`() {
        val element = JDOMUtil.load(javaClass.getResourceAsStream("/saved-state/StagingGroupsSettings.xml")!!)

        assertEquals(SettingsState(autoCleanup = true), XmlSerializer.deserialize(element, SettingsState::class.java))
    }

    fun `test update announces a change once and an unchanged update not at all`() {
        val store = StagingGroupsStore.getInstance(project)
        var announced = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            StagingGroupsListener.TOPIC,
            object : StagingGroupsListener {
                override fun groupsChanged() {
                    announced++
                }
            },
        )

        store.update { GroupsCommands.create(it, "Feature A", id = "a") }
        store.update { it }

        assertEquals(1, announced)
        assertEquals("Feature A", store.snapshot().group("a")?.name)
        assertSame(store.snapshot(), store.snapshot())
    }
}
