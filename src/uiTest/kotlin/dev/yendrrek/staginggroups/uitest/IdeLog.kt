package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot

/** Size of the sandbox's `idea.log` in bytes. */
fun RemoteRobot.ideaLogLength(): Long = callJs<String>(
    """
    importClass(com.intellij.openapi.application.PathManager)
    var log = java.nio.file.Paths.get(PathManager.getLogPath(), 'idea.log')
    '' + (java.nio.file.Files.exists(log) ? java.nio.file.Files.size(log) : 0)
    """,
    true,
).toLong()

/** `idea.log` from byte [offset] on, or from the start if the log has rotated to a shorter file. */
fun RemoteRobot.ideaLogFrom(offset: Long): String = callJs(
    """
    importClass(com.intellij.openapi.application.PathManager)
    var log = java.nio.file.Paths.get(PathManager.getLogPath(), 'idea.log')
    var result = ''
    if (java.nio.file.Files.exists(log)) {
        var bytes = java.nio.file.Files.readAllBytes(log)
        var start = bytes.length < $offset ? 0 : $offset
        result = '' + new java.lang.String(java.util.Arrays.copyOfRange(bytes, start, bytes.length), java.nio.charset.StandardCharsets.UTF_8)
    }
    result
    """,
    true,
)

/**
 * The error entries in [log] that belong to this plugin, each with its trace.
 *
 * An entry belongs to the plugin when its trace runs through the plugin's code, its logger is one of
 * the plugin's, or the platform blames the plugin. A "Last Action:" line naming one of the plugin's
 * actions does not count.
 */
fun pluginErrors(log: String): List<String> {
    val entries = mutableListOf<StringBuilder>()
    log.lineSequence().forEach { line ->
        if (LOG_ENTRY_START.containsMatchIn(line)) entries += StringBuilder(line) else entries.lastOrNull()?.append('\n')?.append(line)
    }
    return entries.map { it.toString() }
        .filter { LOG_ERROR_LEVEL.containsMatchIn(it.lineSequence().first()) }
        .filter { entry -> PLUGIN_OWNED.any { it.containsMatchIn(entry) } }
}

private val LOG_ENTRY_START = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")
private val LOG_ERROR_LEVEL = Regex("""\]\s+(SEVERE|ERROR) - """)
private val PLUGIN_OWNED = listOf(
    Regex("""\bat dev\.yendrrek\.staginggroups\."""),
    Regex("""(SEVERE|ERROR) - #(dev\.yendrrek\.staginggroups|d\.y\.s)\."""),
    Regex("""Plugin to blame: Staging Groups"""),
)
