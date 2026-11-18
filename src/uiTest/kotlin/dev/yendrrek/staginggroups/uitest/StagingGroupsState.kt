package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot

/** Defines the plugin's classes and the open project for a Rhino script run in the sandbox. */
private const val PLUGIN_PRELUDE = """
    importClass(com.intellij.ide.plugins.PluginManagerCore)
    importClass(com.intellij.openapi.extensions.PluginId)
    importClass(com.intellij.openapi.project.ProjectManager)
    var pluginLoader = PluginManagerCore.getPlugin(PluginId.getId('dev.yendrrek.staginggroups')).getPluginClassLoader()
    function pluginClass(name) { return java.lang.Class.forName(name, true, pluginLoader) }
    function companion(name) { return pluginClass(name).getField('Companion').get(null) }
    var project = ProjectManager.getInstance().getOpenProjects()[0]
    var basePath = '' + project.getBasePath()
"""

/**
 * Replaces the project's staging groups with [groups], in that order: each group's name maps to the
 * paths of its files, relative to the project directory. Group IDs are the names.
 */
fun RemoteRobot.setStagingGroups(groups: Map<String, List<String>>) {
    val commands = groups.entries.joinToString("\n") { (name, paths) ->
        val jsPaths = paths.joinToString(", ") { "basePath + '/$it'" }
        """
        state = commands.create(state, '$name', '$name')
        state = commands.assign(state, java.util.List.of($jsPaths), '$name')
        """
    }
    runJs(
        """
        $PLUGIN_PRELUDE
        var commands = pluginClass('dev.yendrrek.staginggroups.domain.GroupsCommands').getField('INSTANCE').get(null)
        var emptyState = pluginClass('dev.yendrrek.staginggroups.domain.GroupsState').getConstructor().newInstance()
        companion('dev.yendrrek.staginggroups.state.StagingGroupsStore').getInstance(project).update(
            new Packages.kotlin.jvm.functions.Function1({
                invoke: function (ignored) {
                    var state = emptyState
                    $commands
                    return state
                }
            })
        )
        """,
        true,
    )
}

/** Switches a grouping of the staging tree on or off directly, without its menu. */
fun RemoteRobot.setStagingTreeGrouping(key: String, enabled: Boolean) {
    stagingTree().runJs("component.getGroupingSupport().set('$key', $enabled)", true)
}

/** The grouping keys the staging tree has switched on. */
fun RemoteRobot.stagingTreeGroupingKeys(): Set<String> =
    stagingTree().callJs<String>("'' + String(component.getGroupingSupport().getGroupingKeys())", true)
        .removeSurrounding("[", "]").split(", ").filter { it.isNotEmpty() }.toSet()

/** Each assigned file, relative to the project directory, mapped to its staging group's name. */
fun RemoteRobot.stagingGroupAssignments(): Map<String, String> =
    callJs<String>(
        """
        $PLUGIN_PRELUDE
        var snapshot = companion('dev.yendrrek.staginggroups.state.StagingGroupsStore').getInstance(project).snapshot()
        var lines = []
        var entries = snapshot.getState().assignments.entrySet().iterator()
        while (entries.hasNext()) {
            var entry = entries.next()
            var group = snapshot.group(entry.getValue())
            lines.push(('' + entry.getKey()).substring(basePath.length + 1) + '=' + (group == null ? '?' : group.name))
        }
        lines.sort()
        '' + lines.join('\n')
        """,
        false,
    ).lines().filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') }

/** The name of every staging group, in display order. */
fun RemoteRobot.stagingGroupList(): List<String> =
    callJs<String>(
        """
        $PLUGIN_PRELUDE
        var groups = companion('dev.yendrrek.staginggroups.state.StagingGroupsStore').getInstance(project).snapshot().getGroups()
        var lines = []
        for (var i = 0; i < groups.size(); i++) lines.push(groups.get(i).name)
        '' + lines.join('\n')
        """,
        false,
    ).lines().filter { it.isNotEmpty() }

/** Saves **Remove committed files from staging groups** as [enabled]. */
fun RemoteRobot.setRemoveCommittedFiles(enabled: Boolean) {
    runJs(
        """
        $PLUGIN_PRELUDE
        companion('dev.yendrrek.staginggroups.settings.StagingGroupsSettings').getInstance(project).update(
            new Packages.kotlin.jvm.functions.Function1({
                invoke: function (state) { return state.copy($enabled) }
            })
        )
        """,
        true,
    )
}
