package app.undo.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import app.undo.Graph
import app.undo.data.Detector
import app.undo.engine.AppCatalog
import app.undo.engine.Classification
import app.undo.engine.Classifier
import app.undo.engine.EventItem
import app.undo.engine.EventType
import app.undo.engine.Money
import app.undo.engine.NotifKind
import app.undo.engine.NotificationInput
import app.undo.engine.ParsedPayment
import app.undo.engine.TextSan
import app.undo.engine.UndoEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android's official, user-granted API for reading notifications (Settings → Notification access).
 * UNDO uses it to: spot payment and renewal notices, keep a private copy of important
 * notifications you swipe away, and stay alive so the settings/apps/media watchers can run.
 * It never cancels, answers or modifies another app's notifications.
 */
class UndoNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        _connected.value = true
        Watchers.start(applicationContext, "listener")
        runCatching { activeNotifications?.forEach { Ingest.onPosted(this, it, record = false) } }
    }

    override fun onListenerDisconnected() {
        instance = null
        _connected.value = false
        Watchers.stop(applicationContext, "listener")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) Ingest.onPosted(this, sbn, record = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn != null) Ingest.onRemoved(this, sbn, reason)
    }

    companion object {
        @Volatile var instance: UndoNotificationListener? = null
            private set
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        fun isEnabled(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun component(ctx: Context) = ComponentName(ctx, UndoNotificationListener::class.java)

        fun requestRebind(ctx: Context) {
            runCatching { NotificationListenerService.requestRebind(component(ctx)) }
        }
    }
}

private object Ingest {
    private const val MIN = 60_000L
    private val handler = Handler(Looper.getMainLooper())

    private data class Pending(val entry: NotificationCache.Entry, val reason: Int, val classification: Classification)
    private val pending = mutableListOf<Pending>()
    private var flushScheduled = false

    fun onPosted(ctx: Context, sbn: StatusBarNotification, record: Boolean) {
        if (sbn.packageName == ctx.packageName) return
        val (input, entry) = extract(ctx, sbn) ?: return
        val c = Classifier.classify(input)
        // One-time codes and system chatter are never kept, not even in memory.
        if (c.kind == NotifKind.OTP || c.kind == NotifKind.NOISE) return
        NotificationCache.put(entry.copy(kind = c.kind, importance = c.importance))
        if (!record) return
        val prefs = Graph.prefs
        when (c.kind) {
            NotifKind.PAYMENT_DEBIT -> if (prefs.isOn(Detector.PAYMENTS) && c.payment != null) recordPayment(ctx, sbn, entry, c.payment)
            NotifKind.SUBSCRIPTION -> if (prefs.isOn(Detector.SUBSCRIPTIONS)) recordSubscription(sbn, entry, c)
            else -> Unit
        }
    }

    fun onRemoved(ctx: Context, sbn: StatusBarNotification, reason: Int) {
        if (sbn.packageName == ctx.packageName) return
        val userAction = reason == NotificationListenerService.REASON_CANCEL ||
            reason == NotificationListenerService.REASON_CANCEL_ALL ||
            reason == NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED
        val cached = NotificationCache.markRemoved(sbn.key)
        if (!userAction || !Graph.prefs.isOn(Detector.NOTIFICATIONS)) return
        val entry = cached ?: extract(ctx, sbn)?.second ?: return
        val classification = cached?.let { Classification(it.kind, it.importance) }
            ?: extract(ctx, sbn)?.first?.let { Classifier.classify(it) } ?: return
        if (!classification.storable) return
        synchronized(pending) { pending += Pending(entry, reason, classification) }
        if (!flushScheduled) {
            flushScheduled = true
            handler.postDelayed({ flush(ctx) }, 1200)
        }
    }

    /** Groups a burst of removals (clear-all, or swiping a group) into one meaningful event. */
    private fun flush(ctx: Context) {
        flushScheduled = false
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        if (batch.isEmpty()) return
        // Children removed because a summary went away only count if the user removed something.
        if (batch.none { it.reason == NotificationListenerService.REASON_CANCEL || it.reason == NotificationListenerService.REASON_CANCEL_ALL }) return
        val nonSummary = batch.filter { !it.entry.groupSummary }
        val real = (nonSummary.ifEmpty { batch }).distinctBy { it.entry.key }
        val clearAll = batch.any { it.reason == NotificationListenerService.REASON_CANCEL_ALL }
        val now = System.currentTimeMillis()

        if (real.size == 1 && !clearAll) {
            val p = real.first()
            val e = p.entry
            Graph.store.insert(
                UndoEvent(
                    type = EventType.NOTIFICATION_DISMISSED,
                    occurredAt = now,
                    packageName = e.packageName,
                    appLabel = e.appLabel,
                    title = e.title ?: e.appLabel ?: "Notification",
                    body = e.text,
                    extras = mapOf("nkey" to e.key, "importance" to p.classification.importance.toString(), "kind" to p.classification.kind.name, "posted" to e.postTime.toString()),
                ),
            )
        } else {
            val apps = real.map { it.entry.packageName }.distinct()
            val title = when {
                clearAll -> "You cleared ${real.size} notifications"
                apps.size == 1 -> "You dismissed ${real.size} ${real.first().entry.appLabel ?: ""} notifications".replace("  ", " ")
                else -> "You dismissed ${real.size} notifications"
            }
            Graph.store.insert(
                UndoEvent(
                    type = EventType.NOTIFICATIONS_CLEARED,
                    occurredAt = now,
                    packageName = apps.singleOrNull(),
                    appLabel = if (apps.size == 1) real.first().entry.appLabel else null,
                    title = title,
                    body = real.take(3).joinToString(" · ") { it.entry.title ?: it.entry.appLabel ?: "" },
                    items = real.sortedByDescending { it.classification.importance }.map {
                        EventItem(
                            title = it.entry.title ?: it.entry.appLabel ?: "Notification",
                            subtitle = listOfNotNull(it.entry.appLabel, it.entry.text).joinToString(" — "),
                            ref = it.entry.key,
                            packageName = it.entry.packageName,
                        )
                    },
                    extras = mapOf("importance" to (real.maxOf { it.classification.importance }).toString()),
                ),
            )
        }
        Graph.purge()
    }

    private fun recordPayment(ctx: Context, sbn: StatusBarNotification, entry: NotificationCache.Entry, p: ParsedPayment) {
        val fromApp = sbn.packageName in AppCatalog.paymentApps
        val title = buildString {
            append("Paid ")
            append(Money.format(p.amountMinor, p.currency))
            p.counterparty?.let { append(" to "); append(it) }
        }
        val event = UndoEvent(
            type = EventType.PAYMENT_SENT,
            occurredAt = sbn.postTime,
            packageName = sbn.packageName,
            appLabel = entry.appLabel,
            title = title,
            body = listOfNotNull(entry.title, entry.text).joinToString(" — "),
            amountMinor = p.amountMinor,
            currency = p.currency,
            counterparty = p.counterparty,
            reference = p.reference,
            extras = mapOf("instrument" to (p.instrument ?: ""), "nkey" to entry.key, "source" to if (fromApp) "app" else "message"),
            dedupeKey = "pay:${p.amountMinor}:${p.currency}",
        )
        // A UPI app and the bank SMS often report the same payment; merge them into one event.
        Graph.store.upsert(event, since = sbn.postTime - 10 * MIN) { existing ->
            val preferNew = fromApp && existing.extras["source"] != "app"
            existing.copy(
                counterparty = existing.counterparty ?: event.counterparty,
                reference = existing.reference ?: event.reference,
                packageName = if (preferNew) event.packageName else existing.packageName,
                appLabel = if (preferNew) event.appLabel else existing.appLabel,
                title = if (existing.counterparty == null && event.counterparty != null) event.title else existing.title,
                extras = if (preferNew) event.extras else existing.extras,
            )
        }
        Alerts.maybePost(ctx, "pay:${p.amountMinor}:${sbn.postTime / (10 * MIN)}", title, "Was that a mistake? UNDO shows the fastest way to get it back.")
        Graph.purge()
    }

    private fun recordSubscription(sbn: StatusBarNotification, entry: NotificationCache.Entry, c: Classification) {
        val title = entry.title ?: "Subscription notice"
        val event = UndoEvent(
            type = EventType.SUBSCRIPTION_NOTICE,
            occurredAt = sbn.postTime,
            packageName = sbn.packageName,
            appLabel = entry.appLabel,
            title = title,
            body = entry.text,
            amountMinor = c.payment?.amountMinor,
            currency = c.payment?.currency,
            counterparty = c.payment?.counterparty,
            expiresAt = c.renewalInMs?.let { sbn.postTime + it },
            extras = mapOf("nkey" to entry.key),
            dedupeKey = "sub:${sbn.packageName}:${title.lowercase().hashCode()}",
        )
        Graph.store.upsert(event, since = sbn.postTime - 24 * 60 * MIN) { existing -> existing.copy(body = event.body, expiresAt = event.expiresAt ?: existing.expiresAt) }
        Graph.purge()
    }

    private fun extract(ctx: Context, sbn: StatusBarNotification): Pair<NotificationInput, NotificationCache.Entry>? = try {
        val n = sbn.notification
        val ex: Bundle = n.extras ?: Bundle.EMPTY
        val title = TextSan.clean(ex.getCharSequence(Notification.EXTRA_TITLE) ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG), 120)
        val text = TextSan.clean(ex.getCharSequence(Notification.EXTRA_TEXT), 400)
        val big = TextSan.clean(ex.getCharSequence(Notification.EXTRA_BIG_TEXT), 600)
        val conversation = TextSan.clean(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE), 120)
        val flags = n.flags
        val ongoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0 || (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val summary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val progress = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val media = ex.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val conversationStyle = ex.containsKey(Notification.EXTRA_MESSAGES)
        val label = AppInfo.label(ctx, sbn.packageName)
        val input = NotificationInput(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = big,
            category = n.category,
            ongoing = ongoing,
            groupSummary = summary,
            hasProgress = progress,
            isMedia = media,
            isConversation = conversationStyle,
        )
        val entry = NotificationCache.Entry(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label,
            title = title,
            text = big ?: text,
            postTime = sbn.postTime,
            kind = NotifKind.OTHER,
            importance = 0,
            contentIntent = n.contentIntent,
            conversation = conversation ?: title,
            groupSummary = summary,
        )
        input to entry
    } catch (_: Throwable) {
        // Malformed extras from another app (e.g. BadParcelableException) must never crash UNDO.
        null
    }
}
