# Staging Groups

IntelliJ Platform plugin that adds named groups to the Git staging area and the Commit view. The
implementation plan is `docs/staging-groups-plan.md` (gitignored): work one stage at a time, and
start the next stage only when the current one's acceptance checks pass in the sandbox IDE.

## Build and test

| Command | What it does |
|---|---|
| `./gradlew build` | Compile and run the fast unit and platform tests |
| `./gradlew runIde` | Sandbox IDE for manual use |
| `./gradlew verifyPlugin -PverifyIde=<version>` | Plugin Verifier against one IDE from `./gradlew printProductsReleases` |

Toolchain is JDK 21; Gradle provisions it.

## Run the UI tests

The `uiTest` source set drives a sandbox IDE through remote-robot. The sandbox opens
`build/ui-test-fixture`, a Git repository that `src/uiTest/fixture/create-fixture-repo.sh` rebuilds
before every launch, with the staging area enabled by `src/uiTest/sandbox-config`.

```bash
Xvfb :99 -screen 0 1920x1080x24 &
env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 DISPLAY=:99 ./gradlew runIdeForUiTests &
./gradlew uiTest
```

- Run the sandbox on Xvfb. On a Wayland display robot input never arrives and screen capture
  blocks forever.
- Failure screenshots land in `build/reports/ui-test/`; screenshots saved for review with
  `saveReviewScreenshot` land in `build/reports/ui-test/review/`.
- A test that passes still fails if the plugin logged an error to `idea.log` during it.
- Find a component's class by dumping the live hierarchy: `curl http://127.0.0.1:8082`. XPath
  `@class` matches the concrete class (the staging tree is `MyChangesTree`).
- `runIdeForUiTestsLatest` runs the same sandbox on the installed IDE set by `uiTestLatestIdePath`
  in `~/.gradle/gradle.properties`. Only one sandbox can run at a time: both listen on port 8082.
- A list popup, such as a table cell's drop-down, ignores remote-robot's clicks. Choose an item by
  typing its text, which filters the list, then pressing Enter.
- Stop Xvfb with `pkill -x Xvfb`.

## Verify a platform API

Never guess a platform signature. No sources jar is downloaded, so inspect the extracted IDE with
`javap`. Build the classpath from every jar directory: classes are spread across `lib/`,
`lib/modules/`, `plugins/` and more, and a partial classpath falsely reports "class not found".

```bash
IDE=$(find ~/.gradle/caches/*/transforms -maxdepth 6 -type d -name 'idea-2026.1.5' | head -1)
CP=$(find "$IDE" -name '*.jar' | sed 's|/[^/]*$||' | sort -u | sed 's|$|/*|' | tr '\n' ':')
javap -cp "$CP" com.intellij.openapi.vcs.changes.ui.SimpleChangesGroupingPolicy
```

Read the whole listing; never pipe it through `head` and reason from what is missing. Use `-p` for
private members, `-v` for annotations, and `-c` for bytecode.

`javap` shows a Kotlin `internal` member as public, and the Plugin Verifier does not report it. Read
the Kotlin visibility from the classes the plugin compiles against:

```bash
./gradlew kotlinVisibility --args='git4idea.index.GitStageTracker git4idea.index.GitStageManagerKt'
```

Top-level functions live in the file's `...Kt` class. A class reported as Java has no Kotlin
visibility; check its annotations with `javap -v`.

## Platform API rules

- Never use a class or member annotated `@ApiStatus.Internal`. `verifyPlugin` fails on one, and
  `acceptedInternalApiUsages` in `build.gradle.kts` stays empty. A class in an `impl` package is
  allowed when it carries no such annotation; a Kotlin-`internal` member never is.
- Never omit a defaulted argument when calling a `com.intellij` Kotlin function or constructor. The
  synthetic `$default` call throws `NoSuchMethodError` on newer IDEs. `PlatformDefaultArgumentCallsTest`
  enforces it.
- The grouping policy and the colour provider run off the EDT and very often: read immutable
  snapshots only, with no I/O and no locks.
- Keep Git-specific code in the optional `staginggroups-git.xml` module, so the plugin loads with
  Git disabled. The rest of the plugin reaches the staging area only through the `vcsChangesView`
  extension point (`VcsChangesView`), which `git/GitStagingAreaView` implements.
- Register listeners in `plugin.xml`, never as `<component>`, so the plugin unloads without a restart.

## Coexistence with PrismTabs

PrismTabs (`~/dev/prismtabs`, ID `io.prismtabs`) also colours editor tabs through
`editorTabColorProvider`.

- Colours are off by default. With colours off, group nodes show `AllIcons.Nodes.Folder` and a bold
  name, and the plugin colours nothing.
- The colour provider is registered with `order="first, after prismtabs"`, so PrismTabs wins on tabs.
- Never take `anchor="first"` in `EditorTabPopupMenu`; that slot is PrismTabs'.
- UI text says "Staging Group", never a bare "Group".
- No `Alt+1…9` shortcuts; they are the IDE's tool-window shortcuts.

## Conventions

- British English in code and UI text.
- Never change the plugin `<id>`, or rename a persisted state class, property or XML name: users
  would lose their saved groups. `PersistenceTest` reads the files in `src/test/resources/saved-state`
  to catch it.
- Persisted state classes are immutable data classes with `@JvmField val` fields. Give every field
  that is not a collection or a map an xmlb annotation (`@field:Attribute`, `@field:OptionTag`):
  the platform's serialiser silently skips final fields without one.
