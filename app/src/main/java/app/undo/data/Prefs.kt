package app.undo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Detector(val key: String, val title: String, val description: String) {
    PAYMENTS("det_payments", "Payments", "Debits from UPI, bank and wallet notifications"),
    SUBSCRIPTIONS("det_subs", "Subscriptions & renewals", "Renewal, trial-ending and AutoPay notices"),
    NOTIFICATIONS("det_notifs", "Swiped-away notifications", "Keeps a private copy of important notifications you dismiss"),
    FILES("det_files", "Deleted photos & videos", "Spots media that is trashed or deleted"),
    APPS("det_apps", "Uninstalled apps", "Notices when an app disappears"),
    SETTINGS("det_settings", "Changed settings", "Rotation, brightness, timeout, font size, sound, DND, airplane"),
}

data class PrefsState(
    val retentionHours: Int,
    val paused: Boolean,
    val enabled: Set<Detector>,
    val alerts: Boolean,
    val hideInRecents: Boolean,
    val blockScreenshots: Boolean,
    val onboarded: Boolean,
    val friendlyTone: Boolean,
)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("undo_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PrefsState> = _state.asStateFlow()
    val current: PrefsState get() = _state.value

    private fun read() = PrefsState(
        retentionHours = sp.getInt("retention_hours", 24),
        paused = sp.getBoolean("paused", false),
        enabled = Detector.values().filter { sp.getBoolean(it.key, true) }.toSet(),
        alerts = sp.getBoolean("alerts", false),
        hideInRecents = sp.getBoolean("hide_recents", true),
        blockScreenshots = sp.getBoolean("block_screens", false),
        onboarded = sp.getBoolean("onboarded", false),
        friendlyTone = sp.getBoolean("friendly_tone", true),
    )

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _state.value = read()
    }

    fun isOn(d: Detector): Boolean = !current.paused && d in current.enabled

    fun setRetention(hours: Int) = edit { putInt("retention_hours", hours.coerceIn(1, 24 * 7)) }
    fun setPaused(v: Boolean) = edit { putBoolean("paused", v) }
    fun setDetector(d: Detector, v: Boolean) = edit { putBoolean(d.key, v) }
    fun setAlerts(v: Boolean) = edit { putBoolean("alerts", v) }
    fun setHideInRecents(v: Boolean) = edit { putBoolean("hide_recents", v) }
    fun setBlockScreenshots(v: Boolean) = edit { putBoolean("block_screens", v) }
    fun setOnboarded() = edit { putBoolean("onboarded", true) }
    fun setFriendlyTone(v: Boolean) = edit { putBoolean("friendly_tone", v) }

    var mediaBaselined: Boolean
        get() = sp.getBoolean("media_baselined", false)
        set(v) { sp.edit().putBoolean("media_baselined", v).apply() }
    var appsBaselined: Boolean
        get() = sp.getBoolean("apps_baselined", false)
        set(v) { sp.edit().putBoolean("apps_baselined", v).apply() }

    fun resetBaselines() {
        sp.edit().putBoolean("media_baselined", false).putBoolean("apps_baselined", false).apply()
    }
}
