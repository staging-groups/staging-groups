package dev.yendrrek.staginggroups.state

import com.intellij.util.messages.Topic

/** Project message-bus listener for changes to staging groups and their settings, on any thread. */
interface StagingGroupsListener {

    /** Groups or file assignments changed. */
    fun groupsChanged() {}

    /** A Staging Groups setting changed. */
    fun settingsChanged() {}

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<StagingGroupsListener> =
            Topic(StagingGroupsListener::class.java, Topic.BroadcastDirection.NONE)
    }
}
