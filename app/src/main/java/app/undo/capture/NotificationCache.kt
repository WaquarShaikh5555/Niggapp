package app.undo.capture

import android.app.PendingIntent
import app.undo.engine.NotifKind

/**
 * In-memory only. Holds the latest notifications (and recently dismissed ones) together with
 * their original tap actions so UNDO can "open where it pointed". Never written to disk; gone
 * when the process dies, which is why the UI always has an "Open app" fallback.
 */
object NotificationCache {
    data class Entry(
        val key: String,
        val packageName: String,
        val appLabel: String?,
        val title: String?,
        val text: String?,
        val postTime: Long,
        val kind: NotifKind,
        val importance: Int,
        val contentIntent: PendingIntent?,
        val conversation: String?,
        val groupSummary: Boolean,
    )

    private const val MAX = 250
    private const val MAX_AGE = 24L * 60 * 60 * 1000
    private val active = LinkedHashMap<String, Entry>()
    private val removed = LinkedHashMap<String, Entry>()

    @Synchronized fun put(e: Entry) {
        active.remove(e.key)
        active[e.key] = e
        trim(active)
    }

    @Synchronized fun get(key: String): Entry? = active[key] ?: removed[key]

    @Synchronized fun markRemoved(key: String): Entry? {
        val e = active.remove(key) ?: return null
        removed.remove(key)
        removed[key] = e
        trim(removed)
        return e
    }

    @Synchronized fun intentFor(key: String?): PendingIntent? = key?.let { (removed[it] ?: active[it])?.contentIntent }

    /** Recent conversations from a messaging app (most recent first) — used by the "wrong message" flow. */
    @Synchronized fun recentConversations(pkg: String, now: Long = System.currentTimeMillis()): List<Entry> =
        (active.values + removed.values)
            .filter { it.packageName == pkg && it.kind == NotifKind.MESSAGE && !it.groupSummary && now - it.postTime < MAX_AGE }
            .sortedByDescending { it.postTime }
            .distinctBy { it.conversation ?: it.title }
            .take(6)

    @Synchronized fun recentPackages(now: Long = System.currentTimeMillis()): Map<String, Long> =
        (active.values + removed.values).filter { now - it.postTime < MAX_AGE }
            .groupBy { it.packageName }.mapValues { (_, v) -> v.maxOf { it.postTime } }

    @Synchronized fun clear() {
        active.clear()
        removed.clear()
    }

    private fun trim(map: LinkedHashMap<String, Entry>) {
        val now = System.currentTimeMillis()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (map.size > MAX || now - e.value.postTime > MAX_AGE) it.remove() else break
        }
    }
}
