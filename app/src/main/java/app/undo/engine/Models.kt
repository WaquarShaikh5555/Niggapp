package app.undo.engine

/**
 * Everything UNDO records is one of these. The list is intentionally short: each type maps to
 * something a third-party Android app can legitimately observe, and something a person might
 * genuinely want to take back.
 */
enum class EventType {
    PAYMENT_SENT,
    SUBSCRIPTION_NOTICE,
    NOTIFICATION_DISMISSED,
    NOTIFICATIONS_CLEARED,
    FILE_TRASHED,
    FILE_DELETED,
    APP_UNINSTALLED,
    SETTING_CHANGED,
}

/** The honest answer to "can it actually be undone?" */
enum class Undoability {
    /** UNDO itself can put it back (with your confirmation). */
    UNDOABLE,
    /** Some of it can be recovered (e.g. the text of a notification, a reinstall). */
    PARTIAL,
    /** Only you can fix it, inside another app — UNDO takes you there and tells you how. */
    GUIDED,
    /** Nobody can reverse it from this phone. UNDO shows the best recovery path instead. */
    NOT_UNDOABLE,
}

enum class EventStatus { OPEN, UNDONE, NOT_A_MISTAKE }

data class EventItem(
    val title: String,
    val subtitle: String? = null,
    /** Opaque reference: a notification key, or a media id like "images:123". */
    val ref: String? = null,
    val expiresAt: Long? = null,
    val packageName: String? = null,
)

data class UndoEvent(
    val id: Long = 0,
    val type: EventType,
    val occurredAt: Long,
    val packageName: String? = null,
    val appLabel: String? = null,
    val title: String,
    val body: String? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val counterparty: String? = null,
    val reference: String? = null,
    /** When the undo window closes (trash expiry, refund window, renewal date...). */
    val expiresAt: Long? = null,
    val items: List<EventItem> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    /** True when UNDO noticed the change later and doesn't know the exact moment. */
    val approxTime: Boolean = false,
    val status: EventStatus = EventStatus.OPEN,
    val resolvedAt: Long? = null,
    val dedupeKey: String? = null,
) {
    val count: Int get() = if (items.isEmpty()) 1 else items.size
}

/** Small, deterministic text hygiene for anything that comes from another app. */
object TextSan {
    // Control chars (except newline) + zero-width + bidi override/isolate characters (spoofing vectors).
    private val unsafe = Regex("[\\u0000-\\u0009\\u000B-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val blankLines = Regex("\\n{3,}")

    fun clean(raw: CharSequence?, max: Int = 400): String? {
        if (raw == null) return null
        var s = unsafe.replace(raw.toString(), "")
        s = spaces.replace(s, " ")
        s = blankLines.replace(s, "\n\n").trim()
        if (s.isEmpty()) return null
        return if (s.length > max) s.substring(0, max).trimEnd() + "…" else s
    }
}
