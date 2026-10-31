package dev.yendrrek.staginggroups

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.StagingGroupsBundle"

/** The plugin's UI text, from `messages/StagingGroupsBundle.properties`. */
object StagingGroupsBundle {
    private val instance = DynamicBundle(StagingGroupsBundle::class.java, BUNDLE)

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) @NonNls key: String, vararg params: Any): String =
        instance.getMessage(key, *params)
}
