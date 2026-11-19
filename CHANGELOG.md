<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Staging Groups Changelog

## [Unreleased]

### Added

- Staging groups: named groups for changed files, in the Git staging area and in the Commit view's
  changelists.
- **Group By | Staging Groups** shows each group as a node under *Staged* and *Unstaged*. Stage,
  unstage or roll back a whole group from its node. *Unstaged*, or the active changelist, lists
  every group, empty ones too.
- **Staging Group** context menu in the Commit view, the staging area, the Project view and editor
  tabs: assign files to a group, create a group, take files out of their group. A folder stands for
  the changed files under it.
- **Settings | Version Control | Staging Groups**: rename, reorder and delete groups.
- Files keep their group when renamed, moved or deleted in the IDE, or renamed with `git mv`.
- **Remove Unchanged Files from Staging Groups**, and the optional setting **Remove committed files
  from staging groups**.
