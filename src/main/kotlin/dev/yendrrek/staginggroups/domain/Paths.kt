package dev.yendrrek.staginggroups.domain

/** Whether this system-independent path is [path] itself or lies anywhere under it. */
internal fun String.isAtOrUnder(path: String): Boolean = when {
    this == path -> true
    path.endsWith('/') -> startsWith(path)
    else -> startsWith(path) && this[path.length] == '/'
}
