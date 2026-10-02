package app.undo.capture

import android.content.Context

/**
 * Starts the lightweight observers while *something* keeps UNDO alive: the notification listener
 * (if granted) or the app being on screen. No foreground service, no polling, no wakelocks.
 */
object Watchers {
    private val holders = mutableSetOf<String>()

    @Synchronized fun start(ctx: Context, holder: String) {
        val app = ctx.applicationContext
        val first = holders.isEmpty()
        holders += holder
        if (first) {
            SettingsWatcher.start(app)
            AppsWatcher.start(app)
            MediaWatcher.start(app)
        }
    }

    @Synchronized fun stop(ctx: Context, holder: String) {
        holders -= holder
        if (holders.isEmpty()) {
            val app = ctx.applicationContext
            SettingsWatcher.stop(app)
            AppsWatcher.stop(app)
            MediaWatcher.stop(app)
        }
    }

    /** Call after a permission is granted so new observers attach immediately. */
    @Synchronized fun refresh(ctx: Context) {
        if (holders.isNotEmpty()) MediaWatcher.start(ctx.applicationContext)
    }
}
