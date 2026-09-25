package prunerecent

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

// Plain file logic, kept free of IntelliJ APIs so it can be unit tested.

private val leftoverRootNames = setOf(".idea", ".DS_Store")
private val leftoverIdeaNames = setOf("workspace.xml", ".DS_Store")

enum class Verdict { KEEP, REMOVE_ENTRY, DELETE_FOLDER_AND_REMOVE_ENTRY }

interface PruneLog {
    fun info(message: String)
    fun warn(message: String, e: Throwable)
}

private fun names(dir: Path): Set<String> =
    Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList().toSet() }

private fun isRegularFile(path: Path) = Files.isRegularFile(path, NOFOLLOW_LINKS)

// True when the folder holds only the .idea/workspace.xml PyCharm writes back on close.
fun isLeftover(dir: Path): Boolean {
    if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) return false
    val root = names(dir)
    if (".idea" !in root || !leftoverRootNames.containsAll(root)) return false
    if (".DS_Store" in root && !isRegularFile(dir.resolve(".DS_Store"))) return false
    val idea = dir.resolve(".idea")
    if (!Files.isDirectory(idea, NOFOLLOW_LINKS)) return false
    val inner = names(idea)
    if ("workspace.xml" !in inner || !leftoverIdeaNames.containsAll(inner)) return false
    return inner.all { isRegularFile(idea.resolve(it)) }
}

fun isStrictlyUnder(dir: Path, root: Path): Boolean {
    if (!Files.isDirectory(root)) return false
    val real = dir.toRealPath()
    val rootReal = root.toRealPath()
    return real != rootReal && real.startsWith(rootReal)
}

fun verdict(dir: Path, openPaths: Set<Path>, codeRoot: Path): Verdict = when {
    dir in openPaths -> Verdict.KEEP
    !Files.exists(dir, NOFOLLOW_LINKS) -> Verdict.REMOVE_ENTRY
    !isLeftover(dir) -> Verdict.KEEP
    isStrictlyUnder(dir, codeRoot) -> Verdict.DELETE_FOLDER_AND_REMOVE_ENTRY
    else -> Verdict.REMOVE_ENTRY
}

// Never recursive: directory deletes fail if anything else appeared in the meantime.
fun deleteLeftover(dir: Path) {
    val idea = dir.resolve(".idea")
    for (name in leftoverIdeaNames) Files.deleteIfExists(idea.resolve(name))
    Files.delete(idea)
    Files.deleteIfExists(dir.resolve(".DS_Store"))
    Files.delete(dir)
}

// Deletes leftover folders and returns the recent paths whose entries should be removed.
fun prune(recentPaths: List<String>, openPaths: Set<Path>, codeRoot: Path, log: PruneLog): List<String> {
    val open = openPaths.map { it.normalize() }.toSet()
    val toRemove = mutableListOf<String>()
    for (recent in recentPaths) {
        try {
            val dir = Path.of(recent).normalize()
            when (verdict(dir, open, codeRoot)) {
                Verdict.KEEP -> {}
                Verdict.REMOVE_ENTRY -> toRemove += recent
                Verdict.DELETE_FOLDER_AND_REMOVE_ENTRY -> {
                    try {
                        deleteLeftover(dir)
                        log.info("Deleted leftover project folder $dir")
                        toRemove += recent
                    } catch (e: Exception) {
                        log.warn("Could not delete leftover project folder $dir", e)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Could not check recent project $recent", e)
        }
    }
    return toRemove
}
