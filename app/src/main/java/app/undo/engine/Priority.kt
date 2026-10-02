package app.undo.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min

/**
 * Decides what deserves attention. Score = (how much this matters + how much it cost + how soon
 * the undo window closes) × how recent it is. Each type decays at its own pace: a dismissed
 * notification stops mattering within the hour; a payment matters all day.
 */
object Priority {
    private const val MIN = 60_000.0
    private const val HOUR = 60 * MIN
    const val ATTENTION_THRESHOLD = 35.0
    const val VISIBLE_THRESHOLD = 6.0

    fun score(e: UndoEvent, now: Long): Double {
        if (e.status != EventStatus.OPEN) return 0.0
        val base = when (e.type) {
            EventType.PAYMENT_SENT -> 70.0
            EventType.FILE_TRASHED -> 62.0
            EventType.FILE_DELETED -> 58.0
            EventType.SUBSCRIPTION_NOTICE -> 55.0
            EventType.APP_UNINSTALLED -> 52.0
            EventType.NOTIFICATIONS_CLEARED -> 44.0 + min(12.0, e.count * 1.5)
            EventType.NOTIFICATION_DISMISSED -> 26.0 + (e.extras["importance"]?.toDoubleOrNull()?.let { it / 4 } ?: 8.0)
            EventType.SETTING_CHANGED -> 40.0
        }
        val cost = e.amountMinor?.let { min(25.0, 6.0 * log10(it / 100.0 + 1.0)) } ?: 0.0
        val urgency = e.expiresAt?.let { exp ->
            val left = exp - now
            when {
                left <= 0 -> -20.0
                left < HOUR -> 25.0
                left < 24 * HOUR -> 15.0
                else -> 0.0
            }
        } ?: 0.0
        val halfLife = when (e.type) {
            EventType.PAYMENT_SENT -> 8 * HOUR
            EventType.FILE_TRASHED, EventType.FILE_DELETED -> 12 * HOUR
            EventType.APP_UNINSTALLED -> 8 * HOUR
            EventType.SUBSCRIPTION_NOTICE -> 36 * HOUR
            EventType.NOTIFICATIONS_CLEARED, EventType.NOTIFICATION_DISMISSED -> 1.5 * HOUR
            EventType.SETTING_CHANGED -> 45 * MIN
        }
        val age = (now - e.occurredAt).coerceAtLeast(0L).toDouble()
        val decay = exp(-age * ln(2.0) / halfLife)
        return (base + cost + urgency) * decay
    }

    fun needsAttention(e: UndoEvent, now: Long): Boolean = score(e, now) >= ATTENTION_THRESHOLD

    /** Attention first (highest score), then the rest by recency. */
    fun rank(events: List<UndoEvent>, now: Long): Pair<List<UndoEvent>, List<UndoEvent>> {
        val open = events.filter { it.status == EventStatus.OPEN }
        val attention = open.filter { needsAttention(it, now) }.sortedByDescending { score(it, now) }
        val earlier = events.filter { it !in attention }.sortedByDescending { it.occurredAt }
        return attention to earlier
    }
}
