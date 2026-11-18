package dev.yendrrek.staginggroups.state

import com.intellij.util.messages.Topic

/** Project message-bus listener for changes to staging groups, on any thread. */
interface StagingGroupsListener {

    /** Groups or file assignments changed. */
    fun groupsChanged() {}

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<StagingGroupsListener> =
            Topic(StagingGroupsListener::class.java, Topic.BroadcastDirection.NONE)
    }
}
