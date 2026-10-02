package app.undo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.undo.engine.EventItem
import app.undo.engine.EventStatus
import app.undo.engine.EventType
import app.undo.engine.UndoEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Local-only history. Plain SQLite in the app's private sandbox (encrypted at rest by Android's
 * file-based encryption). All queries are parameterised. Nothing here ever leaves the device.
 */
class EventStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "undo.db", null, 1) {

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "undo-db").apply { isDaemon = true } }.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _events = MutableStateFlow<List<UndoEvent>?>(null)
    /** null until the first load finishes (drives skeleton loading states). */
    val events: StateFlow<List<UndoEvent>?> = _events.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                pkg TEXT, app_label TEXT,
                title TEXT NOT NULL, body TEXT,
                amount_minor INTEGER, currency TEXT, counterparty TEXT, reference TEXT,
                expires_at INTEGER,
                items TEXT, extras TEXT,
                approx INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'OPEN',
                resolved_at INTEGER,
                dedupe TEXT
            )""",
        )
        db.execSQL("CREATE INDEX idx_events_time ON events(occurred_at)")
        db.execSQL("CREATE INDEX idx_events_dedupe ON events(dedupe)")
        db.execSQL(
            """CREATE TABLE media_snapshot (
                ref TEXT PRIMARY KEY,
                name TEXT, folder TEXT, size INTEGER, date_added INTEGER, trashed INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL("CREATE TABLE app_snapshot (pkg TEXT PRIMARY KEY, label TEXT, installer TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun onConfigure(db: SQLiteDatabase) {
        // Deleted rows are overwritten, not left behind in free pages.
        db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
    }

    fun refresh() = scope.launch { reload() }

    private fun reload() {
        val list = mutableListOf<UndoEvent>()
        readableDatabase.query("events", null, null, null, null, null, "occurred_at DESC", "500").use { c ->
            while (c.moveToNext()) list += c.toEvent()
        }
        _events.value = list
    }

    // ----------------------------------------------------------- writes

    fun insert(e: UndoEvent, onInserted: ((Long) -> Unit)? = null) = scope.launch {
        val id = writableDatabase.insert("events", null, e.toValues())
        reload()
        onInserted?.invoke(id)
    }

    fun update(e: UndoEvent) = scope.launch {
        writableDatabase.update("events", e.toValues(), "id = ?", arrayOf(e.id.toString()))
        reload()
    }

    /** Find an open event with the same dedupe key newer than [since] and let [merge] decide. */
    fun upsert(e: UndoEvent, since: Long, merge: (existing: UndoEvent) -> UndoEvent?) = scope.launch {
        val key = e.dedupeKey
        val existing = if (key == null) null else writableDatabase.query(
            "events", null, "dedupe = ? AND occurred_at >= ? AND status = 'OPEN'",
            arrayOf(key, since.toString()), null, null, "occurred_at DESC", "1",
        ).use { c -> if (c.moveToFirst()) c.toEvent() else null }
        if (existing == null) {
            writableDatabase.insert("events", null, e.toValues())
        } else {
            val merged = merge(existing)
            if (merged == null) writableDatabase.delete("events", "id = ?", arrayOf(existing.id.toString()))
            else writableDatabase.update("events", merged.copy(id = existing.id).toValues(), "id = ?", arrayOf(existing.id.toString()))
        }
        reload()
    }

    fun setStatus(id: Long, status: EventStatus) = scope.launch {
        val v = ContentValues().apply {
            put("status", status.name)
            put("resolved_at", if (status == EventStatus.OPEN) null else System.currentTimeMillis())
        }
        writableDatabase.update("events", v, "id = ?", arrayOf(id.toString()))
        reload()
    }

    fun delete(id: Long) = scope.launch {
        writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
        reload()
    }

    fun purgeOlderThan(cutoff: Long) = scope.launch {
        writableDatabase.delete("events", "occurred_at < ?", arrayOf(cutoff.toString()))
        reload()
    }

    /** Wipes history *and* the comparison snapshots. */
    fun deleteEverything() = scope.launch {
        val db = writableDatabase
        db.delete("events", null, null)
        db.delete("media_snapshot", null, null)
        db.delete("app_snapshot", null, null)
        db.execSQL("VACUUM")
        reload()
    }

    suspend fun get(id: Long): UndoEvent? = withContext(dispatcher) {
        readableDatabase.query("events", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toEvent() else null
        }
    }

    // ------------------------------------------------------ snapshots

    data class MediaRow(val ref: String, val name: String, val folder: String?, val size: Long, val dateAdded: Long, val trashed: Boolean)

    suspend fun mediaSnapshot(): Map<String, MediaRow> = withContext(dispatcher) {
        val map = HashMap<String, MediaRow>()
        readableDatabase.query("media_snapshot", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val r = MediaRow(c.str("ref")!!, c.str("name") ?: "", c.str("folder"), c.long("size") ?: 0, c.long("date_added") ?: 0, (c.long("trashed") ?: 0) == 1L)
                map[r.ref] = r
            }
        }
        map
    }

    suspend fun replaceMediaSnapshot(rows: Collection<MediaRow>) = withContext(dispatcher) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("media_snapshot", null, null)
            rows.forEach { r ->
                db.insert("media_snapshot", null, ContentValues().apply {
                    put("ref", r.ref); put("name", r.name); put("folder", r.folder)
                    put("size", r.size); put("date_added", r.dateAdded); put("trashed", if (r.trashed) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    data class AppRow(val pkg: String, val label: String, val installer: String?)

    suspend fun appSnapshot(): Map<String, AppRow> = withContext(dispatcher) {
        val map = HashMap<String, AppRow>()
        readableDatabase.query("app_snapshot", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val r = AppRow(c.str("pkg")!!, c.str("label") ?: "", c.str("installer"))
                map[r.pkg] = r
            }
        }
        map
    }

    suspend fun putApps(rows: Collection<AppRow>, replaceAll: Boolean) = withContext(dispatcher) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (replaceAll) db.delete("app_snapshot", null, null)
            rows.forEach { r ->
                db.insertWithOnConflict("app_snapshot", null, ContentValues().apply {
                    put("pkg", r.pkg); put("label", r.label); put("installer", r.installer)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun removeApp(pkg: String) = withContext(dispatcher) {
        writableDatabase.delete("app_snapshot", "pkg = ?", arrayOf(pkg))
    }

    // ------------------------------------------------------ mapping

    private fun UndoEvent.toValues() = ContentValues().apply {
        put("type", type.name)
        put("occurred_at", occurredAt)
        put("pkg", packageName)
        put("app_label", appLabel)
        put("title", title)
        put("body", body)
        put("amount_minor", amountMinor)
        put("currency", currency)
        put("counterparty", counterparty)
        put("reference", reference)
        put("expires_at", expiresAt)
        put("items", if (items.isEmpty()) null else JSONArray().also { arr ->
            items.forEach { i ->
                arr.put(JSONObject().apply {
                    put("t", i.title)
                    i.subtitle?.let { put("s", it) }
                    i.ref?.let { put("r", it) }
                    i.expiresAt?.let { put("e", it) }
                    i.packageName?.let { put("p", it) }
                })
            }
        }.toString())
        put("extras", if (extras.isEmpty()) null else JSONObject().also { o -> extras.forEach { (k, v) -> o.put(k, v) } }.toString())
        put("approx", if (approxTime) 1 else 0)
        put("status", status.name)
        put("resolved_at", resolvedAt)
        put("dedupe", dedupeKey)
    }

    private fun Cursor.str(col: String): String? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getLong(it) }

    private fun Cursor.toEvent(): UndoEvent {
        val type = runCatching { EventType.valueOf(str("type")!!) }.getOrDefault(EventType.NOTIFICATION_DISMISSED)
        val items = str("items")?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { idx ->
                    val o = arr.getJSONObject(idx)
                    EventItem(
                        title = o.optString("t"),
                        subtitle = o.optString("s").ifEmpty { null },
                        ref = o.optString("r").ifEmpty { null },
                        expiresAt = if (o.has("e")) o.optLong("e") else null,
                        packageName = o.optString("p").ifEmpty { null },
                    )
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()
        val extras = str("extras")?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                // Read keys into a fresh map: no prototype/inheritance semantics to pollute.
                o.keys().asSequence().associateWith { k -> o.optString(k) }
            }.getOrDefault(emptyMap())
        } ?: emptyMap()
        return UndoEvent(
            id = long("id") ?: 0,
            type = type,
            occurredAt = long("occurred_at") ?: 0,
            packageName = str("pkg"),
            appLabel = str("app_label"),
            title = str("title") ?: "",
            body = str("body"),
            amountMinor = long("amount_minor"),
            currency = str("currency"),
            counterparty = str("counterparty"),
            reference = str("reference"),
            expiresAt = long("expires_at"),
            items = items,
            extras = extras,
            approxTime = (long("approx") ?: 0) == 1L,
            status = runCatching { EventStatus.valueOf(str("status") ?: "OPEN") }.getOrDefault(EventStatus.OPEN),
            resolvedAt = long("resolved_at"),
            dedupeKey = str("dedupe"),
        )
    }
}
