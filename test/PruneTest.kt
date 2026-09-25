package prunerecent

import org.junit.jupiter.api.io.TempDir
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PruneTest {
    @TempDir
    lateinit var tmp: Path

    private val codeRoot get() = tmp.resolve("code").createDirectories()
    private val outside get() = tmp.resolve("outside").createDirectories()

    private val log = object : PruneLog {
        val infos = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        override fun info(message: String) { infos += message }
        override fun warn(message: String, e: Throwable) { warnings += message }
    }

    private fun file(path: Path, text: String = "x"): Path {
        path.parent.createDirectories()
        path.writeText(text)
        return path
    }

    private fun leftover(dir: Path): Path {
        file(dir.resolve(".idea/workspace.xml"), "<project/>")
        return dir
    }

    private fun run(vararg dirs: Path, open: Set<Path> = emptySet()) =
        prune(dirs.map { it.toString() }, open, codeRoot, log)

    @Test
    fun `missing folder is removed`() {
        val dir = codeRoot.resolve("gone")
        assertEquals(listOf(dir.toString()), run(dir))
    }

    @Test
    fun `leftover under code root is deleted and removed`() {
        val dir = leftover(codeRoot.resolve("wt"))
        assertEquals(listOf(dir.toString()), run(dir))
        assertFalse(dir.exists())
        assertTrue(codeRoot.exists())
    }

    @Test
    fun `leftover with DS_Store files is deleted`() {
        val dir = leftover(codeRoot.resolve("wt"))
        file(dir.resolve(".DS_Store"))
        file(dir.resolve(".idea/.DS_Store"))
        assertEquals(listOf(dir.toString()), run(dir))
        assertFalse(dir.exists())
    }

    @Test
    fun `nested leftover under code root is deleted`() {
        val dir = leftover(codeRoot.resolve("sentry/.claude/worktrees/wt"))
        assertEquals(listOf(dir.toString()), run(dir))
        assertFalse(dir.exists())
        assertTrue(dir.parent.exists())
    }

    @Test
    fun `leftover outside code root is removed but not deleted`() {
        val dir = leftover(outside.resolve("wt"))
        assertEquals(listOf(dir.toString()), run(dir))
        assertTrue(dir.resolve(".idea/workspace.xml").exists())
    }

    @Test
    fun `code root itself is never deleted`() {
        leftover(codeRoot)
        assertEquals(listOf(codeRoot.toString()), run(codeRoot))
        assertTrue(codeRoot.resolve(".idea/workspace.xml").exists())
    }

    @Test
    fun `real project is kept`() {
        val dir = leftover(codeRoot.resolve("proj"))
        file(dir.resolve("main.py"))
        assertEquals(emptyList(), run(dir))
        assertTrue(dir.resolve("main.py").exists())
    }

    @Test
    fun `worktree with git file is kept`() {
        val dir = leftover(codeRoot.resolve("wt"))
        file(dir.resolve(".git"), "gitdir: /somewhere")
        assertEquals(emptyList(), run(dir))
        assertTrue(dir.resolve(".git").exists())
    }

    @Test
    fun `extra idea file is kept`() {
        val dir = leftover(codeRoot.resolve("wt"))
        file(dir.resolve(".idea/modules.xml"))
        assertEquals(emptyList(), run(dir))
        assertTrue(dir.resolve(".idea/modules.xml").exists())
    }

    @Test
    fun `idea folder without workspace xml is kept`() {
        val dir = codeRoot.resolve("wt").resolve(".idea").createDirectories().parent
        assertEquals(emptyList(), run(dir))
        assertTrue(dir.exists())
    }

    @Test
    fun `empty folder is kept`() {
        val dir = codeRoot.resolve("empty").createDirectories()
        assertEquals(emptyList(), run(dir))
        assertTrue(dir.exists())
    }

    @Test
    fun `open project is kept`() {
        val gone = codeRoot.resolve("gone")
        val dir = leftover(codeRoot.resolve("wt"))
        assertEquals(emptyList(), run(gone, dir, open = setOf(gone, codeRoot.resolve("./wt"))))
        assertTrue(dir.resolve(".idea/workspace.xml").exists())
    }

    @Test
    fun `symlinked project folder is kept`() {
        val target = leftover(outside.resolve("target"))
        val link = codeRoot.resolve("link").createSymbolicLinkPointingTo(target)
        assertEquals(emptyList(), run(link))
        assertTrue(target.resolve(".idea/workspace.xml").exists())
    }

    @Test
    fun `symlinked idea folder is kept`() {
        val target = leftover(outside.resolve("target")).resolve(".idea")
        val dir = codeRoot.resolve("wt").createDirectories()
        dir.resolve(".idea").createSymbolicLinkPointingTo(target)
        assertEquals(emptyList(), run(dir))
        assertTrue(target.resolve("workspace.xml").exists())
    }

    @Test
    fun `symlinked workspace xml is kept`() {
        val target = file(outside.resolve("workspace.xml"))
        val dir = codeRoot.resolve("wt")
        dir.resolve(".idea").createDirectories().resolve("workspace.xml").createSymbolicLinkPointingTo(target)
        assertEquals(emptyList(), run(dir))
        assertTrue(target.exists())
    }

    @Test
    fun `leftover reached through a symlink out of code root is not deleted`() {
        val real = leftover(outside.resolve("wt"))
        codeRoot.resolve("elsewhere").createSymbolicLinkPointingTo(outside)
        val viaLink = codeRoot.resolve("elsewhere/wt")
        assertEquals(listOf(viaLink.toString()), run(viaLink))
        assertTrue(real.resolve(".idea/workspace.xml").exists())
    }

    @Test
    fun `delete stops when an unexpected file appeared in idea`() {
        val dir = leftover(codeRoot.resolve("wt"))
        val extra = file(dir.resolve(".idea/new.xml"))
        assertFailsWith<DirectoryNotEmptyException> { deleteLeftover(dir) }
        assertTrue(extra.exists())
    }

    @Test
    fun `delete stops when an unexpected file appeared in root`() {
        val dir = leftover(codeRoot.resolve("wt"))
        val extra = file(dir.resolve("main.py"))
        assertFailsWith<DirectoryNotEmptyException> { deleteLeftover(dir) }
        assertTrue(extra.exists())
    }

    @Test
    fun `failed delete keeps the entry and logs`() {
        val dir = leftover(codeRoot.resolve("wt"))
        val idea = dir.resolve(".idea")
        idea.toFile().setWritable(false)
        try {
            assertEquals(emptyList(), run(dir))
            assertEquals(1, log.warnings.size)
            assertTrue(idea.resolve("workspace.xml").exists())
        } finally {
            idea.toFile().setWritable(true)
        }
    }

    @Test
    fun `mixed list only removes dead entries`() {
        val gone = codeRoot.resolve("gone")
        val left = leftover(codeRoot.resolve("left"))
        val real = leftover(codeRoot.resolve("real")).also { file(it.resolve("README.md")) }
        assertEquals(listOf(gone.toString(), left.toString()), run(gone, left, real))
        assertTrue(Files.exists(real.resolve("README.md")))
    }
}
