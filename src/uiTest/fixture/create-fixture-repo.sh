#!/usr/bin/env bash
# Creates the Git repository the UI-test sandbox opens, from scratch, at the directory given:
# committed files with staged, unstaged and untracked changes on top.
set -euo pipefail

repo=${1:?usage: create-fixture-repo.sh <directory>}
rm -rf "$repo"
mkdir -p "$repo/src/util" "$repo/.idea"
cd "$repo"

git init --quiet --initial-branch=main
git config user.name 'UI Test'
git config user.email 'ui-test@example.invalid'
git config commit.gpgsign false
# The IDE's project files are not part of the fixture's changes.
echo '/.idea/' >> .git/info/exclude

for name in Foo Bar Baz Qux; do
    printf 'class %s\n' "$name" > "src/$name.kt"
done
printf 'object Util\n' > src/util/Util.kt
printf 'class Model\n' > src/util/Model.kt
printf '# Fixture\n' > README.md
git add --all
git commit --quiet --message 'Initial commit'

# Staged changes.
printf 'class Foo { val staged = 1 }\n' > src/Foo.kt
printf 'class Bar { val staged = 1 }\n' > src/Bar.kt
git add src/Foo.kt src/Bar.kt

# Unstaged changes.
printf 'class Qux { val unstaged = 1 }\n' > src/Qux.kt
printf 'object Util { val unstaged = 1 }\n' > src/util/Util.kt
printf '# Fixture\n\nUnstaged.\n' > README.md

# A file staged in part: staged edit, then a further unstaged one.
printf 'class Baz { val staged = 1 }\n' > src/Baz.kt
git add src/Baz.kt
printf 'class Baz { val staged = 1; val unstaged = 2 }\n' > src/Baz.kt

# Untracked.
printf 'class Fresh\n' > src/Fresh.kt

cat > .idea/vcs.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="VcsDirectoryMappings">
    <mapping directory="" vcs="Git" />
  </component>
</project>
XML
