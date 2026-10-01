# pycharm-prune-recent-projects

A [LivePlugin](https://github.com/dkandalov/live-plugin) script that removes dead entries from PyCharm's recent projects list while the IDE is running.

An entry is dead when its folder is missing, or when the folder holds only `.idea` (plus `.DS_Store`). PyCharm writes `.idea` back when you close a project whose worktree was removed.

## Behaviour

- Runs when loaded, then every 30 minutes.
- Never touches an open project.
- Removes the entry through `RecentProjectsManager.removePath`, so PyCharm saves the pruned list itself.
- Deletes a leftover folder only when its real path is inside `~/code`. Only `.idea` is deleted recursively, without following symlinks. The project folder is then removed only if empty, so nothing else is ever deleted.
- Logs each removal to `idea.log` under `PruneRecentProjects`.
- Adds a **Remove Missing Recent Projects** action (Find Action) to prune on demand.

Written for PyCharm 2026.2 (`PY-262.10315.174`) with LivePlugin 0.11.0.

## Install

1. Install **LivePlugin** from Settings → Plugins → Marketplace and restart.
2. Link this repo into LivePlugin's folder:

   ```sh
   ln -s "$PWD/plugin" ~/Library/Application\ Support/JetBrains/PyCharm2026.2/live-plugins/prune-recent-projects
   ```

3. In the **Live Plugins** tool window, turn on **Run plugins on IDE start**, then run `prune-recent-projects` once.

## Layout

- `plugin/plugin.kts`: the IDE part (timer, action, recent-projects API).
- `plugin/Prune.kt`: the file rules, with no IntelliJ imports.
- `test/PruneTest.kt`: unit tests. Each test works in its own temp folder, never in `~/code`.

LivePlugin compiles every `.kt` and `.kts` file under its plugin folder, so only `plugin/` is linked. Gradle files stay outside it.

## Tests

Needs a JDK. PyCharm's bundled one works:

```sh
JAVA_HOME=/Applications/PyCharm.app/Contents/jbr/Contents/Home ./gradlew test
```

## Turn off

Unload it in the Live Plugins tool window, or remove the symlink and restart.
