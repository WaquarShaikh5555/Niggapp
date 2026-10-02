package app.undo.ui

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppsOutage
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.undo.Graph
import app.undo.engine.EventType
import app.undo.engine.UndoEvent
import app.undo.engine.Undoability
import java.util.Calendar
import java.util.Date

object Fmt {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
        val d = (now - t).coerceAtLeast(0)
        return when {
            d < 45_000 -> "just now"
            d < HOUR -> "${(d / MIN).coerceAtLeast(1)} min ago"
            d < DAY -> "${d / HOUR} hr ago"
            d < 2 * DAY -> "yesterday"
            else -> "${d / DAY} days ago"
        }
    }

    fun until(t: Long, now: Long = System.currentTimeMillis()): String {
        val d = t - now
        return when {
            d <= 0 -> "closed"
            d < HOUR -> "${(d / MIN).coerceAtLeast(1)} min left"
            d < DAY -> "${d / HOUR} hr left"
            else -> "${d / DAY} days left"
        }
    }

    fun time(t: Long): String = DateFormat.getTimeFormat(Graph.app).format(Date(t))

    fun date(t: Long): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
        cal.timeInMillis = t
        val that = cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
        return if (that == today) "today" else DateFormat.getMediumDateFormat(Graph.app).format(Date(t))
    }

    fun dayHeader(t: Long): String {
        val d = date(t)
        return if (d == "today") "Today" else d
    }

    fun whenLong(t: Long): String = "${time(t)} · ${date(t).replaceFirstChar { it.uppercase() }}"

    fun typeLabel(type: EventType): String = when (type) {
        EventType.PAYMENT_SENT -> "Payment"
        EventType.SUBSCRIPTION_NOTICE -> "Subscription"
        EventType.NOTIFICATION_DISMISSED -> "Swiped-away notification"
        EventType.NOTIFICATIONS_CLEARED -> "Cleared notifications"
        EventType.FILE_TRASHED -> "In Android's trash"
        EventType.FILE_DELETED -> "Deleted media"
        EventType.APP_UNINSTALLED -> "Uninstalled app"
        EventType.SETTING_CHANGED -> "Setting changed"
    }

    fun typeIcon(e: UndoEvent): ImageVector = when (e.type) {
        EventType.PAYMENT_SENT -> if (e.currency == "INR") Icons.Rounded.CurrencyRupee else Icons.Rounded.Payments
        EventType.SUBSCRIPTION_NOTICE -> Icons.Rounded.Subscriptions
        EventType.NOTIFICATION_DISMISSED -> Icons.Rounded.NotificationsOff
        EventType.NOTIFICATIONS_CLEARED -> Icons.Rounded.DeleteSweep
        EventType.FILE_TRASHED -> Icons.Rounded.AutoDelete
        EventType.FILE_DELETED -> Icons.Rounded.PhotoLibrary
        EventType.APP_UNINSTALLED -> Icons.Rounded.AppsOutage
        EventType.SETTING_CHANGED -> Icons.Rounded.Tune
    }

    fun undoLabel(u: Undoability): String = when (u) {
        Undoability.UNDOABLE -> "UNDO can fix this"
        Undoability.PARTIAL -> "Partly recoverable"
        Undoability.GUIDED -> "You can fix it — UNDO shows how"
        Undoability.NOT_UNDOABLE -> "Can't be undone · recovery steps"
    }

    fun undoShort(u: Undoability): String = when (u) {
        Undoability.UNDOABLE -> "Undoable"
        Undoability.PARTIAL -> "Partly"
        Undoability.GUIDED -> "Guided fix"
        Undoability.NOT_UNDOABLE -> "Recovery"
    }
}
