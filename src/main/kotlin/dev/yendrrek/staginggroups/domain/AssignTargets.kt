package dev.yendrrek.staginggroups.domain

/** A file or folder the user selected, by its system-independent path. */
data class SelectedPath(val path: String, val isDirectory: Boolean)

object AssignTargets {

    /**
     * The files that assigning or removing [selection] acts on: each selected file, and in place of
     * each selected folder the changed files under it at this moment. In selection order, without
     * duplicates. [changedPaths] is read only when the selection holds a folder.
     */
    fun resolve(selection: List<SelectedPath>, changedPaths: () -> Collection<String>): List<String> {
        val changed by lazy(LazyThreadSafetyMode.NONE, changedPaths)
        val targets = LinkedHashSet<String>()
        for (selected in selection) {
            if (selected.isDirectory) {
                changed.filterTo(targets) { it.isAtOrUnder(selected.path) }
            } else {
                targets += selected.path
            }
        }
        return targets.toList()
    }
}
