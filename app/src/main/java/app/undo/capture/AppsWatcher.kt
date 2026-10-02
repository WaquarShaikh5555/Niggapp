package app.undo.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.undo.Graph
import app.undo.data.Detector
import app.undo.data.EventStore
import app.undo.engine.EventStatus
import app.undo.engine.EventType
import app.undo.engine.UndoEvent
import kotlinx.coroutines.launch

/**
 * Notices apps disappearing. Visibility comes from the manifest's launcher-intent <queries>
 * (no QUERY_ALL_PACKAGES). A snapshot of launchable apps lets UNDO name an app after it's gone.
 */
object AppsWatcher {
    private var receiver: BroadcastReceiver? = null
    private const val MIN = 60_000L

    fun start(ctx: Context) {
        if (receiver != null) return
        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (pkg == c.packageName) return
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                when (intent.action) {
                    Intent.ACTION_PACKAGE_REMOVED -> if (!replacing) onRemoved(c.applicationContext, pkg)
                    Intent.ACTION_PACKAGE_ADDED -> onAdded(c.applicationContext, pkg)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }
        // Protected system broadcasts. Removal is double-checked against PackageManager before recording.
        ContextCompat.registerReceiver(ctx, rcv, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = rcv
        Graph.store.scope.launch { diff(ctx) }
    }

    fun stop(ctx: Context) {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        receiver = null
    }

    private fun launcherApps(ctx: Context): Map<String, EventStore.AppRow> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ctx.packageName) return@mapNotNull null
                pkg to EventStore.AppRow(pkg, ri.loadLabel(pm)?.toString() ?: pkg, AppInfo.installer(ctx, pkg))
            }.toMap()
    }

    /** Catches uninstalls that happened while UNDO wasn't running (exact time unknown — and the UI says so). */
    suspend fun diff(ctx: Context) {
        val prefs = Graph.prefs
        val current = launcherApps(ctx)
        if (current.isEmpty()) return
        if (!prefs.appsBaselined) {
            Graph.store.putApps(current.values, replaceAll = true)
            prefs.appsBaselined = true
            return
        }
        val snapshot = Graph.store.appSnapshot()
        val missing = (snapshot.keys - current.keys).filter { !AppInfo.isInstalled(ctx, it) }
        // A large drop usually means a work profile or visibility change, not a mass uninstall.
        if (missing.size in 1..8 && prefs.isOn(Detector.APPS)) {
            val now = System.currentTimeMillis()
            missing.forEach { pkg -> record(snapshot.getValue(pkg), now, approx = true) }
        }
        Graph.store.putApps(current.values, replaceAll = true)
    }

    private fun onRemoved(ctx: Context, pkg: String) {
        Graph.store.scope.launch {
            if (AppInfo.isInstalled(ctx, pkg)) return@launch
            val row = Graph.store.appSnapshot()[pkg] ?: return@launch
            Graph.store.removeApp(pkg)
            if (Graph.prefs.isOn(Detector.APPS)) record(row, System.currentTimeMillis(), approx = false)
        }
    }

    private fun onAdded(ctx: Context, pkg: String) {
        Graph.store.scope.launch {
            val label = AppInfo.label(ctx, pkg) ?: pkg
            Graph.store.putApps(listOf(EventStore.AppRow(pkg, label, AppInfo.installer(ctx, pkg))), replaceAll = false)
            // Reinstalled? Then the uninstall has genuinely been undone — mark it, verified by PackageManager.
            Graph.store.events.value.orEmpty()
                .filter { it.type == EventType.APP_UNINSTALLED && it.packageName == pkg && it.status == EventStatus.OPEN }
                .forEach { Graph.store.setStatus(it.id, EventStatus.UNDONE) }
        }
    }

    private fun record(row: EventStore.AppRow, at: Long, approx: Boolean) {
        val event = UndoEvent(
            type = EventType.APP_UNINSTALLED,
            occurredAt = at,
            packageName = row.pkg,
            appLabel = row.label,
            title = "Uninstalled ${row.label}",
            body = if (row.installer == "com.android.vending") "Installed from Google Play" else null,
            extras = mapOfNotNull("installer" to row.installer),
            approxTime = approx,
            dedupeKey = "app:${row.pkg}",
        )
        Graph.store.upsert(event, since = at - 10 * MIN) { it }
        Alerts.maybePost(Graph.app, "app:${row.pkg}", "Uninstalled ${row.label}", "Didn't mean to? Reinstall it in one tap.")
    }

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
        pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
}
