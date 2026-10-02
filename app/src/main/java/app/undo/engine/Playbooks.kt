package app.undo.engine

enum class ActionKind {
    RESTORE_TRASH, OPEN_TRASH, RESTORE_SETTING, OPEN_SETTING_SCREEN, OPEN_ORIGINAL, OPEN_APP, OPEN_URL,
    REINSTALL, DIAL, HELP_ME_FIX, OPEN_PLAY_SUBSCRIPTIONS, OPEN_MESSAGING_GUIDE, COPY_TEXT,
    OPEN_NOTIFICATION_HISTORY, OPEN_CAPABILITIES, OPEN_TOPIC,
}

data class ActionSpec(val kind: ActionKind, val label: String, val arg: String? = null, val hint: String? = null)

data class MessageTemplate(val id: String, val label: String, val friendly: String, val formal: String)

data class Plan(
    val undoability: Undoability,
    val verdictTitle: String,
    val verdictBody: String,
    val primary: ActionSpec?,
    val secondary: List<ActionSpec> = emptyList(),
    val steps: List<String> = emptyList(),
    val templates: List<MessageTemplate> = emptyList(),
    val notes: List<String> = emptyList(),
    val urgentTitle: String? = null,
    val urgentBody: String? = null,
    val urgent: List<ActionSpec> = emptyList(),
) {
    val hasFixFlow: Boolean get() = steps.isNotEmpty() || templates.isNotEmpty()
}

data class PlanContext(
    val now: Long,
    val isIndia: Boolean,
    val hasOriginalIntent: Boolean = false,
    val canWriteSettings: Boolean = false,
    val listenerConnected: Boolean = false,
    val installedGalleries: List<String> = emptyList(),
    val installedPackages: Set<String> = emptySet(),
    val dateText: (Long) -> String = { it.toString() },
    val timeText: (Long) -> String = { it.toString() },
)

enum class Topic(val title: String, val subtitle: String) {
    WRONG_MESSAGE("Sent the wrong message or photo", "Unsend it if the app allows, or smooth it over"),
    DELETED_FILE("Deleted a photo, video or file", "Restore from trash or find it in a bin"),
    WRONG_PAYMENT("Paid the wrong person or amount", "The fastest way to get money back"),
    FRAUD("A payment I didn't make", "Lock things down right now"),
    SUBSCRIPTION("Charged for a subscription or renewal", "Cancel it, and ask for a refund"),
    DISMISSED("Swiped away a notification", "See what it said and open it again"),
    SETTING("Changed a setting by accident", "Switch it back"),
    UNINSTALLED("Uninstalled an app", "Reinstall it and recover your data"),
    LOST_WORK("Closed an app or lost what I was typing", "Places your work might still be"),
}

object Playbooks {

    // ---------------------------------------------------------------- events

    fun forEvent(e: UndoEvent, ctx: PlanContext): Plan = when (e.type) {
        EventType.PAYMENT_SENT -> payment(e, ctx)
        EventType.SUBSCRIPTION_NOTICE -> subscription(e, ctx)
        EventType.NOTIFICATION_DISMISSED -> dismissed(e, ctx)
        EventType.NOTIFICATIONS_CLEARED -> cleared(e)
        EventType.FILE_TRASHED -> trashed(e, ctx)
        EventType.FILE_DELETED -> deleted(e, ctx)
        EventType.APP_UNINSTALLED -> uninstalled(e)
        EventType.SETTING_CHANGED -> setting(e, ctx)
    }

    private fun payment(e: UndoEvent, ctx: PlanContext): Plan {
        val app = e.appLabel ?: "your payment app"
        val upi = e.extras["instrument"] == "UPI" || (e.currency == "INR" && e.packageName in AppCatalog.paymentApps)
        val india = ctx.isIndia || e.currency == "INR"
        val steps = buildList {
            add("Check the transaction in $app: confirm the amount, who it went to" + (e.reference?.let { " and the reference ($it)" } ?: "") + ".")
            add("Ask the recipient to send it back — this is the quickest route and often works. UNDO can write the message.")
            when {
                upi -> {
                    add("No reply? In $app open the transaction → Help / Raise a dispute → choose \"sent to wrong account\" (or similar).")
                    add("Still stuck? Call or write to your bank with the UPI reference. They can ask the recipient's bank to contact them.")
                    add("Unresolved after 30 days? Escalate via the NPCI UPI complaint page, then the RBI Ombudsman (cms.rbi.org.in).")
                }
                e.extras["instrument"] == "Card" -> {
                    add("Paid a merchant by mistake? Ask the merchant for a refund or cancellation first.")
                    add("If they refuse, ask your card issuer about a dispute / chargeback. Keep screenshots.")
                }
                else -> {
                    add("Call your bank right away. Some transfers can be recalled if they haven't settled yet.")
                    add("Keep a record: screenshot the transaction and any messages with the recipient.")
                }
            }
        }
        val urgent = buildList {
            if (india) {
                add(ActionSpec(ActionKind.DIAL, "Call 1930 — Cyber Crime Helpline", "1930"))
                add(ActionSpec(ActionKind.OPEN_URL, "Report at cybercrime.gov.in", "https://cybercrime.gov.in"))
            }
            e.packageName?.let { add(ActionSpec(ActionKind.OPEN_APP, "Block card / UPI in $app", it)) }
        }
        return Plan(
            undoability = Undoability.NOT_UNDOABLE,
            verdictTitle = "UNDO can't reverse a payment",
            verdictBody = "No app on your phone can pull money back from someone else's account — anyone who claims otherwise is lying. " +
                "But you can usually get it back by asking the recipient, and your " + (if (upi) "UPI app and bank" else "bank") + " can step in if they don't respond.",
            primary = e.packageName?.let { ActionSpec(ActionKind.OPEN_APP, "Open $app to check it", it) }
                ?: ActionSpec(ActionKind.HELP_ME_FIX, "Help me get it back"),
            secondary = if (e.packageName != null) listOf(ActionSpec(ActionKind.HELP_ME_FIX, "Help me get it back")) else emptyList(),
            steps = steps,
            templates = paymentTemplates(e, ctx),
            urgentTitle = "Didn't make this payment?",
            urgentBody = "Treat it as fraud. Block your card or UPI first, then report it. " +
                (if (india) "Under RBI rules, reporting an unauthorised electronic transaction within 3 working days can limit your liability — often to zero." else "Report it to your bank today; liability protection usually depends on reporting quickly."),
            urgent = urgent,
        )
    }

    private fun subscription(e: UndoEvent, ctx: PlanContext): Plan {
        val text = ((e.title) + " " + (e.body ?: "")).lowercase()
        val isMandate = "autopay" in text || "auto-pay" in text || "mandate" in text || "auto pay" in text
        val fromPlay = e.packageName == "com.android.vending" || "google play" in text
        val app = e.appLabel ?: "the app"
        val primary = if (isMandate && e.packageName != null)
            ActionSpec(ActionKind.OPEN_APP, "Manage AutoPay in $app", e.packageName)
        else ActionSpec(ActionKind.OPEN_PLAY_SUBSCRIPTIONS, "Open Google Play subscriptions")
        val secondary = buildList {
            if (primary.kind != ActionKind.OPEN_PLAY_SUBSCRIPTIONS) add(ActionSpec(ActionKind.OPEN_PLAY_SUBSCRIPTIONS, "Google Play subscriptions"))
            add(ActionSpec(ActionKind.OPEN_URL, "Request a Google Play refund", "https://play.google.com/store/account/orderhistory"))
            if (e.packageName != null && primary.kind != ActionKind.OPEN_APP) add(ActionSpec(ActionKind.OPEN_APP, "Open $app", e.packageName))
            add(ActionSpec(ActionKind.HELP_ME_FIX, "Help me write a refund request"))
        }
        return Plan(
            undoability = Undoability.GUIDED,
            verdictTitle = if (e.expiresAt != null && e.expiresAt > ctx.now) "Act before it renews" else "You can cancel — UNDO shows where",
            verdictBody = "UNDO can't cancel subscriptions for you, but it takes you straight to the right screen. Cancelling stops future charges; a refund for a charge that already went through has to be requested.",
            primary = primary,
            secondary = secondary,
            steps = buildList {
                add("Cancel first so it can't charge again: Google Play → profile → Payments & subscriptions → Subscriptions → pick it → Cancel.")
                if (fromPlay || !isMandate) add("Charged in the last 48 hours through Google Play? Open your Play order history, pick the charge and tap \"Request a refund\".")
                if (isMandate) add("UPI AutoPay: open your UPI app → AutoPay / Mandates → pause or revoke this mandate.")
                add("Subscribed on a website or inside the app? Cancel in that service's account settings — uninstalling the app does not cancel it.")
                add("Then email the service and ask for a refund. Many will refund an accidental renewal you haven't used.")
            },
            templates = listOf(
                MessageTemplate(
                    "sub_refund", "Ask the service for a refund",
                    friendly = "Hi, my subscription${counterpartyOrApp(e)} renewed by accident on ${ctx.dateText(e.occurredAt)}" +
                        (e.amountMinor?.let { " (${Money.format(it, e.currency)})" } ?: "") +
                        ". I haven't used it since and have now cancelled. Could you please refund this charge? Thanks a lot!",
                    formal = "Hello,\n\nMy subscription${counterpartyOrApp(e)} was renewed unintentionally on ${ctx.dateText(e.occurredAt)}" +
                        (e.amountMinor?.let { " for ${Money.format(it, e.currency)}" } ?: "") +
                        ". I have not used the service since the renewal and have cancelled it. I kindly request a full refund of this charge.\n\nAccount email: [your email]\n\nThank you.",
                ),
            ),
        )
    }

    private fun counterpartyOrApp(e: UndoEvent): String = (e.counterparty ?: e.appLabel)?.let { " to $it" } ?: ""

    private fun dismissed(e: UndoEvent, ctx: PlanContext): Plan {
        val app = e.appLabel ?: "the app"
        return Plan(
            undoability = Undoability.PARTIAL,
            verdictTitle = if (ctx.hasOriginalIntent) "Yes — UNDO can reopen it" else "UNDO kept what it said",
            verdictBody = "Android doesn't let apps put a notification back in your shade. UNDO saved a private copy of its text" +
                (if (ctx.hasOriginalIntent) ", and can still open exactly where it pointed." else ". The original tap action has expired, so UNDO opens $app instead."),
            primary = if (ctx.hasOriginalIntent) ActionSpec(ActionKind.OPEN_ORIGINAL, "Open it in $app")
            else e.packageName?.let { ActionSpec(ActionKind.OPEN_APP, "Open $app", it) },
            secondary = listOfNotNull(
                ActionSpec(ActionKind.COPY_TEXT, "Copy the text"),
                if (ctx.hasOriginalIntent && e.packageName != null) ActionSpec(ActionKind.OPEN_APP, "Open $app", e.packageName) else null,
            ),
            notes = listOf("Saved copies are deleted automatically when your history retention ends."),
        )
    }

    private fun cleared(e: UndoEvent): Plan = Plan(
        undoability = Undoability.PARTIAL,
        verdictTitle = "UNDO kept all ${e.count}",
        verdictBody = "Clear-all wipes notifications for good, but UNDO saved what each one said. Tap any of them below to reopen it (while the original link is still alive) or open its app.",
        primary = null,
        secondary = listOf(ActionSpec(ActionKind.COPY_TEXT, "Copy all as text")),
    )

    private fun trashed(e: UndoEvent, ctx: PlanContext): Plan {
        val exp = e.expiresAt
        val live = exp == null || exp > ctx.now
        val n = e.count
        return if (live) Plan(
            undoability = Undoability.UNDOABLE,
            verdictTitle = "Yes — UNDO can restore " + (if (n == 1) "it" else "all $n"),
            verdictBody = "It's in Android's trash" + (exp?.let { " until ${ctx.dateText(it)}" } ?: "") +
                ". Restoring puts it back exactly where it was. Android will ask you to confirm.",
            primary = ActionSpec(ActionKind.RESTORE_TRASH, if (n == 1) "Restore it" else "Restore all $n"),
            secondary = listOf(ActionSpec(ActionKind.OPEN_TRASH, "See everything in the trash")),
        ) else Plan(
            undoability = Undoability.GUIDED,
            verdictTitle = "The trash window has closed",
            verdictBody = "Android empties its trash automatically after about 30 days. Check whether a cloud backup still has it.",
            primary = galleryAction(ctx),
            secondary = listOf(ActionSpec(ActionKind.OPEN_TRASH, "See what's still in the trash")),
        )
    }

    private fun galleryAction(ctx: PlanContext): ActionSpec? {
        val pkg = ctx.installedGalleries.firstOrNull() ?: return ActionSpec(ActionKind.OPEN_URL, "Check Google Photos bin", "https://photos.google.com/trash")
        val g = AppCatalog.galleries[pkg]!!
        return if (g.url != null) ActionSpec(ActionKind.OPEN_URL, "Open ${g.name} bin", g.url, hint = pkg)
        else ActionSpec(ActionKind.OPEN_APP, "Open ${g.name}", pkg, hint = g.binPath)
    }

    private fun deleted(e: UndoEvent, ctx: PlanContext): Plan {
        val galleries = ctx.installedGalleries.mapNotNull { pkg -> AppCatalog.galleries[pkg]?.let { pkg to it } }
        val steps = buildList {
            if (galleries.isEmpty()) add("Open your gallery app and look for \"Recently deleted\", \"Bin\" or \"Trash\".")
            galleries.forEach { (_, g) -> add("${g.name}: ${g.binPath}.") }
            add("Backed up to Google Photos (or another cloud)? It's still there even after deleting from the phone — check photos.google.com.")
            add("Sent it in a chat? WhatsApp, Telegram etc. often still have a copy in the conversation's media.")
        }
        return Plan(
            undoability = Undoability.GUIDED,
            verdictTitle = "Probably recoverable — check the bin",
            verdictBody = "It's gone from this phone's shared storage. Most gallery apps keep deleted items in their own bin for about 30 days, and backed-up photos stay in the cloud. UNDO can't see inside those bins, so it takes you there.",
            primary = galleryAction(ctx),
            secondary = galleries.drop(1).map { (pkg, g) -> ActionSpec(ActionKind.OPEN_APP, "Open ${g.name}", pkg, hint = g.binPath) } +
                ActionSpec(ActionKind.OPEN_TRASH, "Check Android's trash"),
            steps = steps,
            notes = listOf("Be wary of \"recovery\" apps: on modern Android, permanently deleted files generally can't be recovered without root."),
        )
    }

    private fun uninstalled(e: UndoEvent): Plan {
        val app = e.appLabel ?: e.packageName ?: "the app"
        return Plan(
            undoability = Undoability.PARTIAL,
            verdictTitle = "You can reinstall it — data depends on the app",
            verdictBody = "The app itself comes back in one tap. Apps with an account (WhatsApp, banking, social media) restore your data when you sign in. Apps that only stored things on this phone start fresh unless Android Backup included them.",
            primary = e.packageName?.let { ActionSpec(ActionKind.REINSTALL, "Reinstall $app", it) },
            steps = listOf(
                "Reinstall $app from the Play Store.",
                "Open it and sign in with the same account.",
                "WhatsApp: during setup tap \"Restore\" to bring chats back from your Google Drive backup (only if backup was on).",
                "Re-grant any permissions it asks for.",
            ),
        )
    }

    private fun setting(e: UndoEvent, ctx: PlanContext): Plan {
        val key = e.extras["key"] ?: ""
        val old = e.extras["old"]
        val oldWords = SettingWords.value(key, old)
        val label = SettingWords.label(key)
        val writable = key in setOf(SettingWords.ROTATION, SettingWords.BRIGHTNESS_MODE, SettingWords.TIMEOUT)
        return when {
            writable -> Plan(
                undoability = Undoability.UNDOABLE,
                verdictTitle = "Yes — UNDO can switch it back",
                verdictBody = "$label was $oldWords before." + if (!ctx.canWriteSettings) " The first time, Android asks you to allow UNDO to change system settings." else "",
                primary = ActionSpec(ActionKind.RESTORE_SETTING, "Change back to $oldWords"),
            )
            key == SettingWords.RINGER -> Plan(
                undoability = Undoability.UNDOABLE,
                verdictTitle = "Yes — UNDO can switch it back",
                verdictBody = "Your phone was on $oldWords before. If Android blocks the change (Do Not Disturb rules), UNDO opens Sound settings instead.",
                primary = ActionSpec(ActionKind.RESTORE_SETTING, "Back to $oldWords"),
                secondary = listOf(ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Open Sound settings", "sound")),
            )
            key == SettingWords.DND && ctx.listenerConnected -> Plan(
                undoability = Undoability.UNDOABLE,
                verdictTitle = "Yes — UNDO can switch it back",
                verdictBody = "Do Not Disturb was ${oldWords.lowercase()} before.",
                primary = ActionSpec(ActionKind.RESTORE_SETTING, if (old == "1") "Turn Do Not Disturb off" else "Back to $oldWords"),
                secondary = listOf(ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Open Do Not Disturb settings", "dnd")),
            )
            else -> Plan(
                undoability = Undoability.GUIDED,
                verdictTitle = "One tap away",
                verdictBody = "Android doesn't allow apps to change ${label.lowercase()} directly. UNDO opens the exact screen; it was $oldWords before.",
                primary = ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Open ${label.lowercase()} settings", key),
            )
        }
    }

    // ------------------------------------------------------------- templates

    fun paymentTemplates(e: UndoEvent?, ctx: PlanContext): List<MessageTemplate> {
        val amount = e?.amountMinor?.let { Money.format(it, e.currency) } ?: "[amount]"
        val payee = e?.counterparty ?: "[name]"
        val date = e?.let { ctx.dateText(it.occurredAt) } ?: "[date]"
        val time = e?.let { ctx.timeText(it.occurredAt) } ?: "[time]"
        val ref = e?.reference
        val via = e?.appLabel?.let { " via $it" } ?: ""
        val instrument = e?.extras?.get("instrument") ?: "UPI"
        return listOf(
            MessageTemplate(
                "pay_recipient", "Ask them to send it back",
                friendly = "Hi $payee, I accidentally sent you $amount on $date at $time$via. It was meant for someone else — could you please send it back to me?" +
                    (ref?.let { " (Ref: $it)" } ?: "") + " Thank you so much, I really appreciate it!",
                formal = "Hello $payee,\n\nOn $date at $time I transferred $amount to you by mistake$via" + (ref?.let { " (reference $it)" } ?: "") +
                    ". I would be grateful if you could return the amount to the same account or UPI ID.\n\nThank you for your help.",
            ),
            MessageTemplate(
                "pay_bank", "Tell your bank",
                friendly = "Hi, I sent $amount to the wrong person on $date at $time ($instrument" + (ref?.let { ", ref $it" } ?: "") +
                    "). Can you please help me get it back from the recipient's bank? My registered number is [your number].",
                formal = "Dear Customer Care team,\n\nI made an erroneous $instrument payment of $amount on $date at $time to $payee" +
                    (ref?.let { " (Ref: $it)" } ?: "") + ". The amount was sent to the wrong beneficiary. Please initiate a recovery request with the beneficiary's bank and advise me on next steps.\n\n" +
                    "Registered mobile number: [your number]\nAccount (last 4 digits): [XXXX]\n\nThank you.",
            ),
            MessageTemplate(
                "pay_fraud", "Report an unauthorised payment",
                friendly = "Hi, I did NOT make the payment of $amount on $date at $time" + (ref?.let { " (ref $it)" } ?: "") +
                    ". Please block my card/UPI right away and start a dispute. My registered number is [your number].",
                formal = "Dear Customer Care team,\n\nI did not authorise the payment of $amount on $date at $time" + (if (e?.counterparty != null) " to $payee" else "") +
                    (ref?.let { " (Ref: $it)" } ?: "") + ". Please block further debits on my account and card immediately, register this as an unauthorised transaction and initiate a chargeback / recovery.\n\n" +
                    "Registered mobile number: [your number]\n\nThank you.",
            ),
        )
    }

    val wrongMessageTemplates = listOf(
        MessageTemplate(
            "msg_wrong_person", "It went to the wrong person",
            friendly = "Sorry! That last message wasn't meant for you — please ignore it 🙏",
            formal = "Apologies — my previous message was sent to you in error. Please disregard it.",
        ),
        MessageTemplate(
            "msg_wrong_photo", "I sent the wrong photo",
            friendly = "Oops, I sent the wrong photo by mistake 😅 Could you please delete it? Thanks!",
            formal = "I shared an image with you in error. I'd be grateful if you could delete it without saving or forwarding it. Thank you.",
        ),
        MessageTemplate(
            "msg_correction", "I need to correct it",
            friendly = "Correction to my last message: [what you meant]. Sorry for the confusion!",
            formal = "A correction to my previous message: [correct details]. Apologies for any confusion.",
        ),
        MessageTemplate(
            "msg_too_soon", "I said something I regret",
            friendly = "Hey, I'm sorry about my last message — that came out wrong. Can we talk?",
            formal = "Please accept my apologies for my previous message; it did not reflect what I meant to say.",
        ),
    )

    // --------------------------------------------------------------- topics

    fun forTopic(topic: Topic, ctx: PlanContext): Plan = when (topic) {
        Topic.WRONG_MESSAGE -> Plan(
            undoability = Undoability.GUIDED,
            verdictTitle = "Pick the app — speed matters",
            verdictBody = "Most chat apps let you unsend for a limited time; plain SMS can't be unsent at all. UNDO never deletes messages for you — it shows you the exact taps and opens the chat.",
            primary = null,
            templates = wrongMessageTemplates,
        )
        Topic.DELETED_FILE -> deleted(UndoEvent(type = EventType.FILE_DELETED, occurredAt = ctx.now, title = ""), ctx).copy(
            verdictTitle = "Check Android's trash first",
            verdictBody = "On Android 11+, many apps move deleted photos and videos to a system trash for about 30 days — UNDO can restore those directly. Gallery apps also keep their own bins.",
            primary = ActionSpec(ActionKind.OPEN_TRASH, "Look in Android's trash"),
            secondary = deleted(UndoEvent(type = EventType.FILE_DELETED, occurredAt = ctx.now, title = ""), ctx).primary?.let { listOf(it) } ?: emptyList(),
        )
        Topic.WRONG_PAYMENT -> payment(UndoEvent(type = EventType.PAYMENT_SENT, occurredAt = ctx.now, title = "", currency = if (ctx.isIndia) "INR" else null, extras = if (ctx.isIndia) mapOf("instrument" to "UPI") else emptyMap()), ctx)
            .copy(primary = ActionSpec(ActionKind.HELP_ME_FIX, "Help me get it back"), secondary = emptyList())
        Topic.FRAUD -> Plan(
            undoability = Undoability.NOT_UNDOABLE,
            verdictTitle = "Lock it down first, then report",
            verdictBody = "Every minute counts with fraud. Block the card or UPI in your bank app, then report it. Never share an OTP, PIN or screen with anyone who calls you about this.",
            primary = if (ctx.isIndia) ActionSpec(ActionKind.DIAL, "Call 1930 — Cyber Crime Helpline", "1930") else ActionSpec(ActionKind.HELP_ME_FIX, "Write a report to my bank"),
            secondary = buildList {
                if (ctx.isIndia) add(ActionSpec(ActionKind.OPEN_URL, "Report at cybercrime.gov.in", "https://cybercrime.gov.in"))
                add(ActionSpec(ActionKind.HELP_ME_FIX, "Write a report to my bank"))
            },
            steps = buildList {
                add("Block your card / UPI in your bank or payment app (look for Block, Freeze or Manage card).")
                add("Call your bank's official number — from the back of your card or their website, never from an SMS.")
                if (ctx.isIndia) add("Call 1930 or file at cybercrime.gov.in — quick reports give the best chance of freezing the money.")
                add("Change your UPI PIN / net-banking password if you shared anything.")
                add(if (ctx.isIndia) "Report within 3 working days: RBI rules can limit your liability to zero." else "Report it in writing today — protection usually depends on how fast you report.")
            },
            templates = paymentTemplates(null, ctx).filter { it.id == "pay_fraud" },
        )
        Topic.SUBSCRIPTION -> subscription(UndoEvent(type = EventType.SUBSCRIPTION_NOTICE, occurredAt = ctx.now, title = ""), ctx)
        Topic.DISMISSED -> Plan(
            undoability = Undoability.PARTIAL,
            verdictTitle = "UNDO keeps a private copy",
            verdictBody = "With notification access on, every notification you swipe away (except codes and promotions) is listed below. Android's own Notification history can help too, if it was switched on before.",
            primary = ActionSpec(ActionKind.OPEN_NOTIFICATION_HISTORY, "Open Android's notification history"),
            steps = listOf(
                "Android 11+: Settings → Notifications → Notification history. It only shows notifications from after it was turned on.",
                "Missed call? Your Phone app's Recents still has it.",
                "Message? Open the app — the conversation is still there even if the notification isn't.",
            ),
        )
        Topic.SETTING -> Plan(
            undoability = Undoability.PARTIAL,
            verdictTitle = "Most common settings can be switched back",
            verdictBody = "UNDO notices changes to auto-rotate, adaptive brightness, screen timeout, font size, airplane mode, sound mode and Do Not Disturb while it's active. Anything else: the quick panels are below.",
            primary = null,
            secondary = listOf(
                ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Internet & Wi-Fi", "internet"),
                ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Display & font size", SettingWords.FONT_SCALE),
                ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Sound & vibration", "sound"),
                ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "Bluetooth", "bluetooth"),
                ActionSpec(ActionKind.OPEN_SETTING_SCREEN, "All settings", "all"),
            ),
        )
        Topic.UNINSTALLED -> Plan(
            undoability = Undoability.PARTIAL,
            verdictTitle = "Reinstall, then sign in",
            verdictBody = "UNDO lists apps that disappeared from your phone below. Reinstalling is one tap; your data comes back if the app syncs to an account or Android Backup.",
            primary = null,
            steps = listOf(
                "Play Store → profile → Manage apps & device → Manage → filter \"Not installed\" shows everything you've removed.",
                "Sign in with the same account to restore synced data.",
            ),
            secondary = listOf(ActionSpec(ActionKind.OPEN_URL, "Open Play Store library", "https://play.google.com/store/apps")),
        )
        Topic.LOST_WORK -> Plan(
            undoability = Undoability.GUIDED,
            verdictTitle = "It may still be here",
            verdictBody = "Android can't give UNDO what was on your screen or keyboard (that's a good thing — nothing can secretly record you). These are the places lost work usually survives:",
            primary = null,
            steps = listOf(
                "Recent apps (swipe up and hold): the app may still be open where you left it.",
                "Gboard: tap the clipboard icon — copied text is kept for about an hour.",
                "Chrome: ⋮ → Recent tabs shows recently closed tabs; History has the rest.",
                "Google Docs / Keep / Notion save as you type — reopen the document or check version history.",
                "WhatsApp or other chats: an unsent draft stays in the chat's text box.",
                "Email: check the Drafts folder.",
            ),
        )
    }
}
