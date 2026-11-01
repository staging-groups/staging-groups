package dev.yendrrek.staginggroups.lifecycle

/**
 * Whether the plugin is being unloaded. Once started, it never stops: the colour provider goes quiet,
 * so a repaint queued before the platform drops it leaves no colour behind.
 */
object Unloading {

    @Volatile
    var started: Boolean = false
        private set

    fun start() {
        started = true
    }
}
