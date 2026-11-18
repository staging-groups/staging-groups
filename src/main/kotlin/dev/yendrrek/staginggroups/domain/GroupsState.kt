package dev.yendrrek.staginggroups.domain

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap

// The XML annotations are what makes the platform's serialiser store these final fields: without
// one it skips every final field that is not a collection or a map.

/**
 * A named staging group. [order] is the group's position among all groups, from 0.
 *
 * Persisted: never rename this class or its properties, or change their XML names.
 */
@Tag("group")
data class StagingGroup(
    @field:Attribute("id") @JvmField val id: String = "",
    @field:Attribute("name") @JvmField val name: String = "",
    @field:Attribute("order") @JvmField val order: Int = 0,
)

/**
 * Every staging group of a project and the files assigned to them.
 *
 * [groups] is sorted by [StagingGroup.order], numbered from 0 without gaps. [assignments] maps a
 * file's system-independent path to the ID of its only group.
 *
 * Persisted: never rename this class or its properties, or change their XML names.
 */
data class GroupsState(
    @field:XCollection(propertyElementName = "groups")
    @JvmField val groups: List<StagingGroup> = emptyList(),
    @field:XMap(propertyElementName = "assignments", entryTagName = "file", keyAttributeName = "path", valueAttributeName = "group")
    @JvmField val assignments: Map<String, String> = emptyMap(),
)
