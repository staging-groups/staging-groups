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
 * Rereads the whole project directory from disk into the VFS, then marks every file dirty, so the
 * Git views reread the repository. The VFS must be told: without a file watcher, as on Xvfb, it
 * misses a file it knows that was deleted outside the IDE. Asynchronous: wait for the expected tree
 * afterwards.
 */
fun RemoteRobot.refreshVcs() {
    // A refresh changes the VFS, which needs a write-safe context: robot-server's own EDT calls run
    // in any modality, and a synchronous refresh from its thread blocks it.
    runJs(
        """
        importClass(com.intellij.openapi.application.ApplicationManager)
        importClass(com.intellij.openapi.application.ModalityState)
        importClass(com.intellij.openapi.project.ProjectManager)
        importClass(com.intellij.openapi.vfs.LocalFileSystem)
        importClass(com.intellij.openapi.vfs.VfsUtil)
        importClass(com.intellij.openapi.vfs.VirtualFile)
        importClass(com.intellij.openapi.vcs.changes.VcsDirtyScopeManager)
        var project = ProjectManager.getInstance().getOpenProjects()[0]
        ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
            run: function () {
                // A typed array: it picks the VirtualFile overload of markDirtyAndRefresh.
                var roots = java.lang.reflect.Array.newInstance(VirtualFile, 1)
                roots[0] = LocalFileSystem.getInstance().findFileByPath(project.getBasePath())
                VfsUtil.markDirtyAndRefresh(false, true, true, roots)
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            }
        }), ModalityState.nonModal())
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

/** Renames or moves [from] to [to] in the fixture repository, as `git mv` in a terminal does, and makes the IDE notice. */
fun RemoteRobot.gitMoveInFixture(from: String, to: String) {
    runProcess("git", "-C", fixtureRepo.path, "mv", from, to)
    refreshVcs()
}

/** Commits what is staged in the fixture repository, as `git commit` in a terminal does, and makes the IDE notice. */
fun RemoteRobot.commitStagedInFixture() {
    runProcess("git", "-C", fixtureRepo.path, "commit", "--quiet", "--message", "Commit the staged changes")
    refreshVcs()
}
