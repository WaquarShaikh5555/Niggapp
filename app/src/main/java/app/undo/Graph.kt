package app.undo

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import app.undo.capture.Alerts
import app.undo.data.EventStore
import app.undo.data.Prefs
import java.util.Locale
import java.util.TimeZone

class UndoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Tiny manual service locator — no DI framework needed for an app this size. */
object Graph {
    lateinit var app: Application
        private set
    lateinit var store: EventStore
        private set
    lateinit var prefs: Prefs
        private set

    private const val HOUR = 60L * 60 * 1000

    fun init(application: Application) {
        app = application
        prefs = Prefs(application)
        store = EventStore(application)
        Alerts.ensureChannel(application)
        purge()
    }

    /** Enforce the user's retention window. Called on start, on resume and after each capture. */
    fun purge() {
        store.purgeOlderThan(System.currentTimeMillis() - prefs.current.retentionHours * HOUR)
    }

    /** Used only to pick region-specific recovery advice (e.g. India's 1930 helpline). Never stored or sent. */
    val isIndia: Boolean by lazy {
        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = runCatching { tm?.simCountryIso }.getOrNull()?.lowercase()
        val net = runCatching { tm?.networkCountryIso }.getOrNull()?.lowercase()
        sim == "in" || net == "in" || Locale.getDefault().country.equals("IN", ignoreCase = true) ||
            TimeZone.getDefault().id in setOf("Asia/Kolkata", "Asia/Calcutta")
    }
}
