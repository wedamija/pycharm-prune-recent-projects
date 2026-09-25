import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import liveplugin.registerAction
import prunerecent.PruneLog
import prunerecent.prune
import java.nio.file.Path
import java.util.concurrent.TimeUnit

// Removes recent-project entries whose folder is gone or is only a PyCharm leftover.
// See Prune.kt for the rules.

val log = Logger.getInstance("PruneRecentProjects")
val codeRoot: Path = Path.of(System.getProperty("user.home"), "code")

val pruneLog = object : PruneLog {
    override fun info(message: String) = log.info(message)
    override fun warn(message: String, e: Throwable) = log.warn(message, e)
}

fun openProjectPaths(): Set<Path> =
    ProjectManager.getInstance().openProjects.mapNotNull { it.basePath?.let { p -> Path.of(p).normalize() } }.toSet()

fun runPrune() {
    val recent = RecentProjectsManagerBase.getInstanceEx().getRecentPaths()
    val toRemove = prune(recent, openProjectPaths(), codeRoot, pruneLog)
    log.info("Prune ran: checked ${recent.size} recent projects, ${toRemove.size} to remove")
    if (toRemove.isEmpty()) return
    ApplicationManager.getApplication().invokeLater {
        val stillOpen = openProjectPaths()
        val manager = RecentProjectsManager.getInstance()
        for (path in toRemove) {
            if (Path.of(path).normalize() in stillOpen) continue
            manager.removePath(path)
            log.info("Removed missing recent project $path")
        }
    }
}

fun safePrune() {
    try { runPrune() } catch (e: Exception) { log.warn("Recent projects prune failed", e) }
}

val task = AppExecutorUtil.getAppScheduledExecutorService()
    .scheduleWithFixedDelay(::safePrune, 0, 30, TimeUnit.MINUTES)
Disposer.register(pluginDisposable) { task.cancel(false) }
log.info("Loaded; pruning now, then every 30 minutes")

registerAction("Remove Missing Recent Projects", disposable = pluginDisposable) {
    log.info("Manual prune requested")
    AppExecutorUtil.getAppExecutorService().execute(::safePrune)
}
