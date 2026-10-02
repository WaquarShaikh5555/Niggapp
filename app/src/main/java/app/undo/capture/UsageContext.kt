package app.undo.capture

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Optional. With Usage access (granted by you in Settings), UNDO can answer "which app was I just
 * in?" so the "wrong message" flow can jump straight to it. It reads app names and times only —
 * never screen content — and only when you open that flow. Nothing is stored.
 */
object UsageContext {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Package → last time it was in the foreground, within [windowMs]. Most recent first. */
    fun recentApps(ctx: Context, windowMs: Long = 30 * 60_000L): List<Pair<String, Long>> {
        if (!hasAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(end - windowMs, end) }.getOrNull() ?: return emptyList()
        val last = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            // ACTIVITY_RESUMED (API 29) has the same value (1) as the older MOVE_TO_FOREGROUND.
            if (e.eventType == 1 && e.packageName != ctx.packageName) last[e.packageName] = e.timeStamp
        }
        return last.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
