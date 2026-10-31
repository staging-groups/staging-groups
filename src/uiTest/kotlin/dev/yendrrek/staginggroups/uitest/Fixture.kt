package dev.yendrrek.staginggroups.uitest

import com.intellij.remoterobot.RemoteRobot
import java.io.File
import java.util.concurrent.TimeUnit

/** The fixture repository `runIdeForUiTests` opens, as seen from the test JVM (run from the project directory). */
val fixtureRepo: File = File("build/ui-test-fixture").absoluteFile

/** Puts the fixture repository back to its starting changes and makes the IDE notice. */
fun RemoteRobot.resetFixtureRepo() {
    runProcess("bash", "src/uiTest/fixture/create-fixture-repo.sh", fixtureRepo.path, "--reset")
    refreshVcs()
}

/** Paths staged in the fixture repository, relative to it. */
fun stagedPaths(): Set<String> = runProcess("git", "-C", fixtureRepo.path, "diff", "--cached", "--name-only")
    .lines().filter { it.isNotBlank() }.toSet()

/**
 * Refreshes the VFS from disk and marks every file dirty, so the Git views reread the repository.
 * Asynchronous: wait for the expected tree afterwards.
 */
fun RemoteRobot.refreshVcs() {
    runJs(
        """
        importClass(com.intellij.openapi.project.ProjectManager)
        importClass(com.intellij.openapi.vfs.VirtualFileManager)
        importClass(com.intellij.openapi.vcs.changes.VcsDirtyScopeManager)
        VirtualFileManager.getInstance().asyncRefresh(null)
        VcsDirtyScopeManager.getInstance(ProjectManager.getInstance().getOpenProjects()[0]).markEverythingDirty()
        """,
        false,
    )
}

/** Runs [command] and returns its standard output; fails on a non-zero exit or after a minute. */
private fun runProcess(vararg command: String): String {
    val stdout = File.createTempFile("ui-test-", ".out")
    val stderr = File.createTempFile("ui-test-", ".err")
    try {
        val process = ProcessBuilder(*command).redirectOutput(stdout).redirectError(stderr).start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("${command.joinToString(" ")} timed out")
        }
        check(process.exitValue() == 0) { "${command.joinToString(" ")} failed:\n${stderr.readText()}" }
        return stdout.readText()
    } finally {
        stdout.delete()
        stderr.delete()
    }
}

/** Deletes [relativePath] from the fixture repository's working tree and makes the IDE notice. */
fun RemoteRobot.deleteFromFixture(relativePath: String) {
    check(fixtureRepo.resolve(relativePath).delete()) { "could not delete $relativePath" }
    refreshVcs()
}
