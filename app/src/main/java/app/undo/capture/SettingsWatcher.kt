package app.undo.capture

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.undo.Graph
import app.undo.data.Detector
import app.undo.engine.EventType
import app.undo.engine.SettingWords
import app.undo.engine.UndoEvent
import app.undo.engine.EventStatus
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

enum class RestoreResult { DONE, NEEDS_WRITE_PERMISSION, OPENED_SETTINGS, FAILED }

/**
 * Watches a handful of public system settings via ContentObservers and system broadcasts.
 * Restores use only what Android allows: WRITE_SETTINGS (granted by you, for rotation, adaptive
 * brightness and timeout), AudioManager for sound mode, and the notification listener's own
 * Do-Not-Disturb request. Everything else opens the exact settings screen instead.
 */
object SettingsWatcher {
    private class Spec(val key: String, val uri: Uri, val read: (ContentResolver) -> String?)

    private val specs = listOf(
        Spec(SettingWords.ROTATION, Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)) {
            Settings.System.getInt(it, Settings.System.ACCELEROMETER_ROTATION, -1).takeIf { v -> v >= 0 }?.toString()
        },
        Spec(SettingWords.BRIGHTNESS_MODE, Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)) {
            Settings.System.getInt(it, Settings.System.SCREEN_BRIGHTNESS_MODE, -1).takeIf { v -> v >= 0 }?.toString()
        },
        Spec(SettingWords.TIMEOUT, Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT)) {
            Settings.System.getInt(it, Settings.System.SCREEN_OFF_TIMEOUT, -1).takeIf { v -> v > 0 }?.toString()
        },
        Spec(SettingWords.FONT_SCALE, Settings.System.getUriFor(Settings.System.FONT_SCALE)) {
            Settings.System.getFloat(it, Settings.System.FONT_SCALE, 1f).toString()
        },
        Spec(SettingWords.AIRPLANE, Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON)) {
            Settings.Global.getInt(it, Settings.Global.AIRPLANE_MODE_ON, 0).toString()
        },
    )

    private val values = ConcurrentHashMap<String, String>()
    private val suppressUntil = ConcurrentHashMap<String, Long>()
    private var observer: ContentObserver? = null
    private var receiver: BroadcastReceiver? = null
    private val main = Handler(Looper.getMainLooper())
    private const val MERGE_WINDOW = 20_000L

    fun start(ctx: Context) {
        if (observer != null) return
        val cr = ctx.contentResolver
        specs.forEach { s -> s.read(cr)?.let { values[s.key] = it } }
        readRinger(ctx)?.let { values[SettingWords.RINGER] = it }
        values[SettingWords.DND] = readDnd(ctx).toString()

        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val spec = specs.firstOrNull { it.uri == uri } ?: return
                spec.read(cr)?.let { onValue(spec.key, it) }
            }
        }
        specs.forEach { cr.registerContentObserver(it.uri, false, obs) }
        observer = obs

        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> readRinger(c)?.let { onValue(SettingWords.RINGER, it) }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> onValue(SettingWords.DND, readDnd(c).toString())
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        // Protected system broadcasts; the handler re-reads the real state from the system rather than
        // trusting intent extras, so even a spoofed broadcast could not inject a fake event.
        ContextCompat.registerReceiver(ctx, rcv, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = rcv
    }

    fun stop(ctx: Context) {
        observer?.let { ctx.contentResolver.unregisterContentObserver(it) }
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        observer = null
        receiver = null
    }

    private fun readRinger(ctx: Context): String? = (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode?.toString()
    private fun readDnd(ctx: Context): Int = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter

    private fun onValue(key: String, new: String) {
        val old = values.put(key, new)
        if (old == null || old == new) return
        if ((suppressUntil[key] ?: 0L) > System.currentTimeMillis()) return
        if (!Graph.prefs.isOn(Detector.SETTINGS)) return
        if (key == SettingWords.DND && new == "0") return // "unknown" state while the listener reconnects
        val now = System.currentTimeMillis()
        val event = UndoEvent(
            type = EventType.SETTING_CHANGED,
            occurredAt = now,
            title = SettingWords.title(key, old, new),
            body = "Was ${SettingWords.value(key, old)}",
            extras = mapOf("key" to key, "old" to old, "new" to new),
            dedupeKey = "set:$key",
        )
        // Fiddling with a setting creates one event, not ten. Switching it back cancels the event.
        Graph.store.upsert(event, since = now - MERGE_WINDOW) { existing ->
            val firstOld = existing.extras["old"]
            if (firstOld == new) null
            else existing.copy(
                occurredAt = now,
                title = SettingWords.title(key, firstOld, new),
                body = "Was ${SettingWords.value(key, firstOld)}",
                extras = existing.extras + ("new" to new),
            )
        }
        Graph.purge()
    }

    private fun suppress(key: String) {
        suppressUntil[key] = System.currentTimeMillis() + 4_000
    }

    fun canWrite(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    /** Puts a setting back. Always verifies by reading the value again — never assumes success. */
    suspend fun restore(ctx: Context, e: UndoEvent): RestoreResult {
        val key = e.extras["key"] ?: return RestoreResult.FAILED
        val old = e.extras["old"] ?: return RestoreResult.FAILED
        val cr = ctx.contentResolver
        val result = when (key) {
            SettingWords.ROTATION, SettingWords.BRIGHTNESS_MODE, SettingWords.TIMEOUT -> {
                if (!Settings.System.canWrite(ctx)) return RestoreResult.NEEDS_WRITE_PERMISSION
                val name = when (key) {
                    SettingWords.ROTATION -> Settings.System.ACCELEROMETER_ROTATION
                    SettingWords.BRIGHTNESS_MODE -> Settings.System.SCREEN_BRIGHTNESS_MODE
                    else -> Settings.System.SCREEN_OFF_TIMEOUT
                }
                suppress(key)
                runCatching { Settings.System.putInt(cr, name, old.toInt()) }
                delay(250)
                if (specs.first { it.key == key }.read(cr) == old) RestoreResult.DONE else RestoreResult.FAILED
            }
            SettingWords.RINGER -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                suppress(key)
                runCatching { am.ringerMode = old.toInt() }
                delay(250)
                if (am.ringerMode.toString() == old) RestoreResult.DONE
                else if (Launch.settingsScreen(ctx, "sound")) RestoreResult.OPENED_SETTINGS else RestoreResult.FAILED
            }
            SettingWords.DND -> {
                suppress(key)
                val target = old.toInt()
                val listener = UndoNotificationListener.instance
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                when {
                    listener != null -> runCatching { listener.requestInterruptionFilter(target) }
                    nm.isNotificationPolicyAccessGranted -> runCatching { nm.setInterruptionFilter(target) }
                }
                delay(700)
                if (nm.currentInterruptionFilter == target) RestoreResult.DONE
                else if (Launch.settingsScreen(ctx, "dnd")) RestoreResult.OPENED_SETTINGS else RestoreResult.FAILED
            }
            else -> if (Launch.settingsScreen(ctx, key)) RestoreResult.OPENED_SETTINGS else RestoreResult.FAILED
        }
        if (result == RestoreResult.DONE) {
            values[key] = old
            Graph.store.setStatus(e.id, EventStatus.UNDONE)
        }
        return result
    }
}
