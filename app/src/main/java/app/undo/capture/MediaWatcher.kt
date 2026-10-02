package app.undo.capture

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.undo.Graph
import app.undo.data.Detector
import app.undo.data.EventStore
import app.undo.engine.EventItem
import app.undo.engine.EventType
import app.undo.engine.UndoEvent
import kotlinx.coroutines.launch

data class TrashedMedia(
    val uri: Uri,
    val ref: String,
    val name: String,
    val folder: String?,
    val size: Long,
    val expiresAt: Long?,
    val isVideo: Boolean,
)

/**
 * Photos & videos only (that's what Android lets a normal app read). Compares a small snapshot
 * of the last 30 days of media metadata — names, folders, sizes; never the pictures themselves —
 * to spot items that were moved to Android's trash or removed. Restoring from Android's trash
 * uses MediaStore.createTrashRequest, which always shows Android's own confirmation dialog.
 */
object MediaWatcher {
    enum class Access { NONE, PARTIAL, FULL }

    private const val DAY = 24L * 60 * 60 * 1000
    private const val WINDOW = 30 * DAY
    private const val LIMIT = 1500
    private val main = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private var pendingDiff: Runnable? = null

    private val imagesUri: Uri get() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videoUri: Uri get() = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun access(ctx: Context): Access {
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO) -> Access.FULL
                granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) -> Access.PARTIAL
                Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
                else -> Access.NONE
            }
            else -> if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
        }
    }

    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val trashSupported: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun start(ctx: Context) {
        if (observer != null || access(ctx) != Access.FULL) return
        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = scheduleDiff(ctx)
        }
        ctx.contentResolver.registerContentObserver(imagesUri, true, obs)
        ctx.contentResolver.registerContentObserver(videoUri, true, obs)
        observer = obs
        scheduleDiff(ctx)
    }

    fun stop(ctx: Context) {
        observer?.let { ctx.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun scheduleDiff(ctx: Context) {
        pendingDiff?.let { main.removeCallbacks(it) }
        val r = Runnable { Graph.store.scope.launch { diff(ctx) } }
        pendingDiff = r
        main.postDelayed(r, 2500) // a multi-select delete fires many changes; wait for it to settle
    }

    private data class Scanned(val row: EventStore.MediaRow, val expiresAt: Long?)

    private fun scan(ctx: Context): Map<String, Scanned> {
        val out = LinkedHashMap<String, Scanned>()
        val since = (System.currentTimeMillis() - WINDOW) / 1000
        listOf("images" to imagesUri, "video" to videoUri).forEach { (prefix, uri) ->
            val cols = mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, "bucket_display_name", MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED)
            if (trashSupported) {
                cols += MediaStore.MediaColumns.IS_TRASHED
                cols += MediaStore.MediaColumns.DATE_EXPIRES
            }
            val cursor = runCatching {
                if (Build.VERSION.SDK_INT >= 30) {
                    val args = Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.DATE_ADDED} >= ?")
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(since.toString()))
                        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                        putInt(ContentResolver.QUERY_ARG_LIMIT, LIMIT)
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                    ctx.contentResolver.query(uri, cols.toTypedArray(), args, null)
                } else {
                    ctx.contentResolver.query(uri, cols.toTypedArray(), "${MediaStore.MediaColumns.DATE_ADDED} >= ?", arrayOf(since.toString()), "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                }
            }.getOrNull() ?: return@forEach
            cursor.use { c ->
                val idI = c.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameI = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val folderI = c.getColumnIndex("bucket_display_name")
                val sizeI = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val addedI = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val trashI = if (trashSupported) c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1
                val expI = if (trashSupported) c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1
                var n = 0
                while (c.moveToNext() && n < LIMIT) {
                    n++
                    val ref = "$prefix:${c.getLong(idI)}"
                    val row = EventStore.MediaRow(
                        ref = ref,
                        name = (if (nameI >= 0) c.getString(nameI) else null) ?: "Untitled",
                        folder = if (folderI >= 0) c.getString(folderI) else null,
                        size = if (sizeI >= 0) c.getLong(sizeI) else 0,
                        dateAdded = if (addedI >= 0) c.getLong(addedI) * 1000 else 0,
                        trashed = trashI >= 0 && c.getInt(trashI) == 1,
                    )
                    val exp = if (expI >= 0 && !c.isNull(expI)) c.getLong(expI) * 1000 else null
                    out[ref] = Scanned(row, exp)
                }
            }
        }
        return out
    }

    suspend fun diff(ctx: Context) {
        if (access(ctx) != Access.FULL) return
        val prefs = Graph.prefs
        val current = scan(ctx)
        if (!prefs.mediaBaselined) {
            Graph.store.replaceMediaSnapshot(current.values.map { it.row })
            prefs.mediaBaselined = true
            return
        }
        val snapshot = Graph.store.mediaSnapshot()
        val now = System.currentTimeMillis()
        // Items that merely aged out of the window (or past the scan limit) are not "deleted".
        val oldestVisible = if (current.size >= LIMIT) current.values.minOf { it.row.dateAdded } else now - WINDOW + DAY
        val trashed = snapshot.values.filter { !it.trashed && current[it.ref]?.row?.trashed == true }
        val gone = snapshot.values.filter { !it.trashed && current[it.ref] == null && it.dateAdded > oldestVisible }

        val massDrop = gone.size > 25 && gone.size > snapshot.size / 2 // permission/SD-card change, not a real delete
        if (!massDrop && prefs.isOn(Detector.FILES)) {
            if (trashed.isNotEmpty()) record(EventType.FILE_TRASHED, trashed.map { r -> item(r, current[r.ref]?.expiresAt) }, now)
            if (gone.isNotEmpty()) record(EventType.FILE_DELETED, gone.map { item(it, null) }, now)
        }
        Graph.store.replaceMediaSnapshot(current.values.map { it.row })
    }

    private fun item(r: EventStore.MediaRow, expiresAt: Long?) = EventItem(
        title = r.name,
        subtitle = listOfNotNull(r.folder, humanSize(r.size)).joinToString(" · "),
        ref = r.ref,
        expiresAt = expiresAt,
    )

    private fun record(type: EventType, items: List<EventItem>, now: Long) {
        val trash = type == EventType.FILE_TRASHED
        val noun = noun(items)
        val title = if (trash) "Moved $noun to the trash" else "Deleted $noun"
        val event = UndoEvent(
            type = type,
            occurredAt = now,
            title = title,
            body = items.take(3).joinToString(", ") { it.title },
            expiresAt = if (trash) items.mapNotNull { it.expiresAt }.minOrNull() else null,
            items = items,
            dedupeKey = if (trash) "media:trash" else "media:deleted",
        )
        Graph.store.upsert(event, since = now - 3 * 60_000) { existing ->
            val merged = (existing.items + items).distinctBy { it.ref }
            existing.copy(
                occurredAt = now,
                items = merged,
                title = if (trash) "Moved ${noun(merged)} to the trash" else "Deleted ${noun(merged)}",
                body = merged.take(3).joinToString(", ") { it.title },
                expiresAt = if (trash) merged.mapNotNull { it.expiresAt }.minOrNull() else null,
            )
        }
        Alerts.maybePost(Graph.app, "media:${type.name}", title, if (trash) "UNDO can restore it." else "UNDO can show you where to find it.")
        Graph.purge()
    }

    private fun noun(items: List<EventItem>): String {
        val videos = items.count { it.ref?.startsWith("video:") == true }
        val photos = items.size - videos
        return when {
            items.size == 1 -> if (videos == 1) "a video" else "a photo"
            videos == 0 -> "$photos photos"
            photos == 0 -> "$videos videos"
            else -> "${items.size} photos & videos"
        }
    }

    fun uriFor(ref: String): Uri? {
        val (prefix, id) = ref.split(":").takeIf { it.size == 2 } ?: return null
        val base = when (prefix) { "images" -> imagesUri; "video" -> videoUri; else -> return null }
        return id.toLongOrNull()?.let { ContentUris.withAppendedId(base, it) }
    }

    /** Everything in Android's trash that UNDO is allowed to see. */
    fun trashList(ctx: Context): List<TrashedMedia> {
        if (!trashSupported || access(ctx) == Access.NONE) return emptyList()
        val out = mutableListOf<TrashedMedia>()
        listOf(Triple("images", imagesUri, false), Triple("video", videoUri, true)).forEach { (prefix, uri, isVideo) ->
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_EXPIRES} ASC")
            }
            val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, "bucket_display_name", MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_EXPIRES)
            runCatching { ctx.contentResolver.query(uri, cols, args, null) }.getOrNull()?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += TrashedMedia(
                        uri = ContentUris.withAppendedId(uri, id),
                        ref = "$prefix:$id",
                        name = c.getString(1) ?: "Untitled",
                        folder = c.getString(2),
                        size = c.getLong(3),
                        expiresAt = if (c.isNull(4)) null else c.getLong(4) * 1000,
                        isVideo = isVideo,
                    )
                }
            }
        }
        return out.sortedBy { it.expiresAt ?: Long.MAX_VALUE }
    }

    /** Android shows its own "Allow UNDO to restore…?" dialog. Nothing happens without it. */
    fun restoreRequest(ctx: Context, uris: List<Uri>): IntentSender? {
        if (!trashSupported || uris.isEmpty()) return null
        return runCatching { MediaStore.createTrashRequest(ctx.contentResolver, uris, false).intentSender }.getOrNull()
    }

    /** Count how many of [uris] are genuinely out of the trash now. */
    fun countRestored(ctx: Context, uris: List<Uri>): Int {
        if (!trashSupported) return 0
        return uris.count { uri ->
            val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
            runCatching {
                ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), args, null)?.use { c ->
                    c.moveToFirst() && c.getInt(0) == 0
                } ?: false
            }.getOrDefault(false)
        }
    }

    fun humanSize(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        else -> "${bytes / 1024} KB"
    }
}
