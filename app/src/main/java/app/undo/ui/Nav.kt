package app.undo.ui

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import app.undo.capture.Alerts
import app.undo.capture.MediaWatcher
import app.undo.capture.SettingsWatcher
import app.undo.capture.UndoNotificationListener
import app.undo.capture.UsageContext
import app.undo.engine.PlanContext
import app.undo.engine.AppCatalog
import app.undo.capture.AppInfo
import app.undo.Graph

sealed interface Route {
    data object Home : Route
    data class Event(val id: Long) : Route
    data class Fix(val eventId: Long?, val topic: app.undo.engine.Topic?) : Route
    data object Panic : Route
    data class TopicR(val topic: app.undo.engine.Topic) : Route
    data class Messaging(val pkg: String) : Route
    data object Trash : Route
    data object Capabilities : Route
    data object Settings : Route
    data object History : Route
    data object Onboarding : Route
}

class AppViewModel : ViewModel() {
    val stack = mutableStateListOf<Route>(Route.Home)
    /** Bumped on resume so permission-dependent UI re-reads the real system state. */
    val capsTick = mutableIntStateOf(0)
    /** A setting restore waiting for the user to grant "modify system settings". */
    var pendingRestore: Long? = null

    val current: Route get() = stack.last()

    fun go(r: Route) {
        if (stack.lastOrNull() != r) stack.add(r)
    }

    fun replace(r: Route) {
        stack.clear()
        stack.add(r)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun bump() {
        capsTick.intValue++
    }
}

data class Caps(
    val listener: Boolean,
    val media: MediaWatcher.Access,
    val usage: Boolean,
    val writeSettings: Boolean,
    val alerts: Boolean,
) {
    val detectorsOn: Int get() = listOf(listener, media == MediaWatcher.Access.FULL).count { it } + 2 // apps + settings need no permission
    val detectorsTotal: Int get() = if (app.undo.BuildConfig.HAS_LISTENER) 4 else 3

    companion object {
        fun read(ctx: Context) = Caps(
            listener = app.undo.BuildConfig.HAS_LISTENER && UndoNotificationListener.isEnabled(ctx),
            media = MediaWatcher.access(ctx),
            usage = UsageContext.hasAccess(ctx),
            writeSettings = SettingsWatcher.canWrite(ctx),
            alerts = Alerts.permissionGranted(ctx) && Graph.prefs.current.alerts,
        )
    }
}

fun planContext(ctx: Context, hasOriginal: Boolean = false): PlanContext = PlanContext(
    now = System.currentTimeMillis(),
    isIndia = Graph.isIndia,
    hasOriginalIntent = hasOriginal,
    canWriteSettings = SettingsWatcher.canWrite(ctx),
    listenerConnected = UndoNotificationListener.instance != null,
    installedGalleries = AppCatalog.galleries.keys.filter { AppInfo.isInstalled(ctx, it) },
    dateText = { Fmt.date(it) },
    timeText = { Fmt.time(it) },
)
