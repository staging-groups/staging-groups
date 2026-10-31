import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
val pluginVersion = providers.gradleProperty("pluginVersion").get()
version = pluginVersion

// The releases the Plugin Verifier checks. pluginVerification.ides.select{} and
// printProductsReleases both read these, so a CI matrix built from printProductsReleases is the
// list a local verifyPlugin uses.
val verifiedIdeType = IntelliJPlatformType.IntellijIdea
val verifiedIdeChannels = listOf(
    ProductRelease.Channel.RELEASE,
    ProductRelease.Channel.EAP,
    ProductRelease.Channel.RC,
)

sourceSets {
    // Slow, needs a live sandbox IDE started separately; never part of `./gradlew test`.
    create("uiTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val uiTestImplementation: Configuration = configurations.getByName("uiTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.getByName("uiTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
dependencies {
    intellijPlatform {
        // Compile against the OLDEST supported platform and run forward.
        intellijIdea(providers.gradleProperty("platformVersion").get())
        bundledPlugin("Git4Idea")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    // BasePlatformTestCase is JUnit 3/4; the vintage engine runs it on the JUnit Platform next to
    // the plain JUnit 5 tests.
    testImplementation(libs.junit4)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    // kotlin.stdlib.default.dependency=false removes the stdlib everywhere; uiTest does not get the
    // platform's copy, so it needs its own.
    uiTestImplementation(kotlin("stdlib"))
    uiTestImplementation(libs.remote.robot)
    uiTestImplementation(libs.remote.fixtures)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // The Kotlin bundled with the OLDEST supported IDE (261 ships 2.3). The plugin runs on the
        // IDE's stdlib, so a newer API level compiles fine and throws NoSuchMethodError there.
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

changelog {
    version = providers.gradleProperty("pluginVersion")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        // `pluginVersion`, the String above: inside this block `version` is the Property itself,
        // which never matches a changelog section. Fall back to [Unreleased] because patchChangelog
        // moves its entries into the stamped section; reading [Unreleased] alone ships empty notes.
        changeNotes = provider {
            changelog.renderItem(
                (changelog.getOrNull(pluginVersion) ?: changelog.getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML,
            )
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Deliberately unset: the Plugin Verifier guards compatibility, not a ceiling that locks
            // users out on every IDE upgrade.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // -PverifyIde=<version> verifies one release (printProductsReleases output without its
            // "IU-" prefix). The whole matrix in one run needs tens of GB of disk.
            val verifyIde = providers.gradleProperty("verifyIde")
            if (verifyIde.isPresent) {
                create(verifiedIdeType, verifyIde.get())
            } else {
                select {
                    types = listOf(verifiedIdeType)
                    channels = verifiedIdeChannels
                }
            }
        }

        // INTERNAL_API_USAGES: Marketplace refuses an upload over it. MISSING_DEPENDENCIES: an
        // unresolvable <depends> stops the plugin loading, and otherwise only prints.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
        )
    }

    // Environment variables only, so a value can never be committed in gradle.properties.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    test {
        useJUnitPlatform()
        // The platform's test class loader rules out the CDS archive anyway; switching it off
        // stops the JVM warning about it on every run.
        jvmArgs("-Xshare:off")
    }

    // One release per line, matching what verifyPlugin checks.
    printProductsReleases {
        types = listOf(verifiedIdeType)
        channels = verifiedIdeChannels
    }

    runIde {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf("-Didea.is.internal=true")
        }
    }

    // Start `runIdeForUiTests` in the background first, then run this against it.
    register<Test>("uiTest") {
        description = "Runs remote-robot UI tests against a `runIdeForUiTests` IDE started separately."
        group = "verification"
        testClassesDirs = sourceSets["uiTest"].output.classesDirs
        classpath = sourceSets["uiTest"].runtimeClasspath
        useJUnitPlatform()
        // remote-robot's Gson parsing reflects into Throwable; without this every failure surfaces
        // as an opaque IllegalArgumentException.
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        // Never up to date: the result depends on the live IDE, not on these inputs.
        outputs.upToDateWhen { false }
    }
}

// The Git repository the UI-test sandbox opens, rebuilt from scratch before every launch so each
// run starts from the same staged, unstaged and untracked changes.
val uiTestFixtureRepo = layout.buildDirectory.dir("ui-test-fixture")
val createUiTestFixtureRepo = tasks.register<Exec>("createUiTestFixtureRepo") {
    description = "Recreates the Git repository the UI-test sandbox IDE opens."
    group = "verification"
    val script = layout.projectDirectory.file("src/uiTest/fixture/create-fixture-repo.sh")
    inputs.file(script)
    outputs.upToDateWhen { false }
    commandLine("bash", script.asFile.absolutePath, uiTestFixtureRepo.get().asFile.absolutePath)
}

// Internal platform API this plugin may call. Keep it empty: an entry is a decision to ship an API
// the Marketplace may refuse, made in review with the reasoning written down, never to turn a build
// green. Format: "com.example.PlatformClass.member used by dev.yendrrek.staginggroups.Caller".
val acceptedInternalApiUsages = emptySet<String>()

/**
 * Fails on any `@ApiStatus.Internal` platform usage the Verifier reports that is not in
 * [acceptedInternalApiUsages], naming the member and the IDEs that reported it.
 *
 * Kept alongside INTERNAL_API_USAGES in failureLevel because that level is one switch for the whole
 * category: drop it to accept one call and every other internal call goes silent. Members also
 * become internal in later IDEs, so the Verifier's report is the only signal there is.
 *
 * Finalizes verifyPlugin so a plain `./gradlew verifyPlugin` cannot skip it, and does not depend on
 * it, so re-reading a report on disk costs no IDE downloads.
 */
val verifyInternalApiAllowlist = tasks.register("verifyInternalApiAllowlist") {
    description = "Fails on any internal platform API usage the Verifier reports that is not accepted in build.gradle.kts."
    group = "verification"

    val reportsDirectory = tasks.named<VerifyPluginTask>("verifyPlugin").flatMap { it.verificationReportsDirectory }
    val accepted = acceptedInternalApiUsages
    outputs.upToDateWhen { false }

    doLast {
        val reportsRoot = reportsDirectory.get().asFile

        // No report at all means nothing was checked, never that the plugin is clean.
        val ideReports = reportsRoot.listFiles()?.filter { it.isDirectory }.orEmpty().sortedBy { it.name }
        check(ideReports.isNotEmpty()) {
            "no Plugin Verifier reports under $reportsRoot, so nothing was checked. If verifyPlugin " +
                "itself failed above, fix that first."
        }

        // "Internal <kind> <member> is <verb> in|by <user>. This <kind> is marked with @…Internal …"
        val usage = Regex("""^Internal \w+ (.+?) is \w+ (?:in|by) (.+)$""")

        // Signatures and synthetic `$1` suffixes are dropped so an entry survives a recompile.
        fun normalise(line: String): String? {
            val head = line.substringBeforeLast(". This ")
            val (member, user) = usage.find(head)?.destructured ?: return null
            val calledMember = member.substringBefore('(').substringBefore(" : ").trim()
            val callingClass = user.substringBefore('(')
                .let { if ('(' in user) it.substringBeforeLast('.') else it }
                .substringBefore('$')
                .trim()
            return "$calledMember used by $callingClass"
        }

        val offenders = sortedMapOf<String, MutableSet<String>>()
        val seen = mutableSetOf<String>()
        var reportsRead = 0

        ideReports.forEach { ide ->
            // Absent when that IDE found no internal usage: a pass.
            ide.walkTopDown().filter { it.isFile && it.name == "internal-api-usages.txt" }.forEach { report ->
                reportsRead++
                report.readLines()
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        // An unparseable line fails: a changed report format must not pass silently.
                        val normalised = normalise(line.trim())
                            ?: error("could not parse a Plugin Verifier finding in $report; its report format has changed:\n  $line")
                        seen += normalised
                        if (normalised !in accepted) {
                            offenders.getOrPut(normalised) { sortedSetOf() } += ide.name
                        }
                    }
            }
        }

        if (offenders.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Internal platform API used without being accepted in build.gradle.kts:")
                    offenders.forEach { (finding, ides) -> appendLine("  $finding  [${ides.joinToString(", ")}]") }
                    appendLine()
                    appendLine("JetBrains Marketplace refuses an upload that uses internal API.")
                    append("Replace the call with public API; dump and read the whole type before concluding none exists.")
                },
            )
        }

        // A de-internalised accepted call is good news, not a failure.
        (accepted - seen).forEach {
            logger.lifecycle("verifyInternalApiAllowlist: no longer reported, so the entry can go: $it")
        }
        logger.lifecycle("verifyInternalApiAllowlist: ${ideReports.size} IDE report(s), $reportsRead with internal usage, all accepted.")
    }
}

tasks.named("verifyPlugin") {
    finalizedBy(verifyInternalApiAllowlist)
}

intellijPlatformTesting {
    runIde {
        val uiTestIdeArgs = listOf(
            "-Drobot-server.port=8082",
            // X11, also on a Wayland desktop: robot input and screen capture need it, and the
            // sandbox runs on Xvfb. The IDE's own `auto` picks Wayland whenever a compositor is up.
            "-Dawt.toolkit.name=XToolkit",
            // No first-run walls for the robot to get stuck behind.
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false",
            "-Didea.trust.all.projects=true",
            "-Dide.show.tips.on.startup.default.value=false",
            "-Didea.is.internal=true",
            // Overrides the -Didea.required.plugins.id=<this plugin> IPGP adds. From 2026.2 that
            // property makes the platform refuse to unload the plugin, so an unload test cannot do
            // what a user's Disable does.
            "-Didea.required.plugins.id=com.jetbrains.test.robot-server-plugin",
            // macOS native chrome remote-robot cannot see or drive.
            "-DjbScreenMenuBar.enabled=false",
            "-Dapple.laf.useScreenMenuBar=false",
            "-Dide.mac.file.chooser.native=false",
        )

        // Application settings every UI-test sandbox starts with (the Git staging area enabled).
        val sandboxConfigSeed = layout.projectDirectory.dir("src/uiTest/sandbox-config")
        fun PrepareSandboxTask.seedSandboxConfig() {
            val seed = sandboxConfigSeed.asFile
            val configDirectory = sandboxConfigDirectory
            inputs.dir(seed)
            doLast { seed.copyRecursively(configDirectory.get().asFile, overwrite = true) }
        }

        register("runIdeForUiTests") {
            task {
                dependsOn(createUiTestFixtureRepo)
                argumentProviders += CommandLineArgumentProvider { listOf(uiTestFixtureRepo.get().asFile.absolutePath) }
                jvmArgumentProviders += CommandLineArgumentProvider { uiTestIdeArgs }
            }
            prepareSandboxTask { seedSandboxConfig() }
            plugins {
                robotServerPlugin(libs.versions.remoteRobot.get())
            }
        }

        // The same sandbox on an INSTALLED newest IDE, since platform behaviour moves under the
        // plugin. Installed rather than downloaded: Gradle 9 holds the download cache immutable,
        // and an IDE that starts JCEF writes into its own install directory, which breaks the next
        // launch. The path is machine-specific, so it lives in ~/.gradle/gradle.properties.
        // Only one sandbox can run at a time: both listen on :8082.
        register("runIdeForUiTestsLatest") {
            localPath = layout.dir(
                providers.gradleProperty("uiTestLatestIdePath")
                    .orElse(
                        provider {
                            throw GradleException(
                                "runIdeForUiTestsLatest needs uiTestLatestIdePath in ~/.gradle/gradle.properties: " +
                                    "the directory of an installed IntelliJ IDEA, e.g. a Toolbox app directory.",
                            )
                        },
                    )
                    .map { file(it) },
            )
            task {
                dependsOn(createUiTestFixtureRepo)
                argumentProviders += CommandLineArgumentProvider { listOf(uiTestFixtureRepo.get().asFile.absolutePath) }
                jvmArgumentProviders += CommandLineArgumentProvider { uiTestIdeArgs }
            }
            prepareSandboxTask { seedSandboxConfig() }
            plugins {
                robotServerPlugin(libs.versions.remoteRobot.get())
            }
        }
    }
}
