package app.undo.engine

/**
 * What UNDO knows about specific apps. Every capability statement here describes what *the user*
 * can do inside that app — UNDO never acts inside other apps.
 *
 * Unsend windows reflect each app's published behaviour at the time of writing and are phrased
 * cautiously because apps change them; the UI always tells people to check in the app itself.
 */
data class MessagingGuide(
    val name: String,
    val canUnsend: Boolean,
    val unsendLabel: String,
    val window: String,
    /** Approximate window in ms for urgency hints; null = no known limit / not applicable. */
    val windowMs: Long?,
    val steps: List<String>,
    val edit: String? = null,
    val caveat: String,
)

data class GalleryGuide(val name: String, val binPath: String, val url: String? = null)

object AppCatalog {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    private val whatsapp = MessagingGuide(
        name = "WhatsApp",
        canUnsend = true,
        unsendLabel = "Delete for everyone",
        window = "About 2 days after sending",
        windowMs = 48 * HOUR,
        steps = listOf(
            "Open the chat and long-press the message (or photo).",
            "Tap the bin icon → \"Delete for everyone\".",
            "Selecting several messages? Tap each one first, then the bin icon.",
        ),
        edit = "Typo instead? Long-press → ⋮ → Edit works for about 15 minutes after sending.",
        caveat = "The chat will show \"This message was deleted\". If they already saw it, or their phone saved the photo, deleting won't take that back.",
    )

    val messagingApps: Map<String, MessagingGuide> = mapOf(
        "com.whatsapp" to whatsapp,
        "com.whatsapp.w4b" to whatsapp.copy(name = "WhatsApp Business"),
        "org.telegram.messenger" to MessagingGuide(
            name = "Telegram",
            canUnsend = true,
            unsendLabel = "Delete — also for the other person",
            window = "Private chats: no time limit",
            windowMs = null,
            steps = listOf(
                "Open the chat and long-press the message.",
                "Tap Delete and tick \"Also delete for …\" before confirming.",
            ),
            edit = "Long-press → Edit to fix a typo instead (up to about 48 hours).",
            caveat = "In some groups only admins can delete for everyone. Anyone who already read it has seen it.",
        ),
        "com.instagram.android" to MessagingGuide(
            name = "Instagram",
            canUnsend = true,
            unsendLabel = "Unsend",
            window = "Any time",
            windowMs = null,
            steps = listOf("Open the chat and long-press the message.", "Tap \"Unsend\" and confirm."),
            edit = "Long-press → Edit fixes a typo for about 15 minutes after sending.",
            caveat = "They may have seen it or have a notification preview already.",
        ),
        "com.facebook.orca" to MessagingGuide(
            name = "Messenger",
            canUnsend = true,
            unsendLabel = "Unsend for everyone",
            window = "Usually any time (check the option appears)",
            windowMs = null,
            steps = listOf("Long-press the message.", "Tap \"Remove\" → \"Unsend for everyone\"."),
            edit = "Long-press → Edit fixes a typo for about 15 minutes.",
            caveat = "Messenger shows that a message was unsent.",
        ),
        "org.thoughtcrime.securesms" to MessagingGuide(
            name = "Signal",
            canUnsend = true,
            unsendLabel = "Delete for everyone",
            window = "Within about 24 hours of sending",
            windowMs = 24 * HOUR,
            steps = listOf("Long-press the message.", "Tap Delete → \"Delete for everyone\"."),
            edit = "Long-press → Edit also works within about 24 hours.",
            caveat = "Recipients on old Signal versions may still see it.",
        ),
        "com.google.android.apps.messaging" to MessagingGuide(
            name = "Google Messages",
            canUnsend = false,
            unsendLabel = "Delete for everyone (RCS only, if offered)",
            window = "SMS/MMS: can't be unsent. RCS chats: only shortly after sending, on newer versions",
            windowMs = 15 * MIN,
            steps = listOf(
                "Long-press the message. If you see \"Delete for everyone\", it's an RCS chat — use it now.",
                "If you only see \"Delete\", that removes it from your phone only. It has already been delivered.",
                "Send a short follow-up so they know to ignore it — UNDO can draft one.",
            ),
            caveat = "Classic SMS/MMS messages are delivered by your carrier instantly; no app can recall them.",
        ),
        "com.samsung.android.messaging" to MessagingGuide(
            name = "Samsung Messages",
            canUnsend = false,
            unsendLabel = "Not available for SMS",
            window = "SMS/MMS can't be unsent",
            windowMs = null,
            steps = listOf(
                "Deleting it in Samsung Messages only removes your copy.",
                "Send a short follow-up asking them to ignore it — UNDO can draft one.",
            ),
            caveat = "SMS is delivered by your carrier instantly; no app can recall it.",
        ),
        "com.google.android.gm" to MessagingGuide(
            name = "Gmail",
            canUnsend = true,
            unsendLabel = "Undo (bottom of the screen)",
            window = "Only 5–30 seconds after tapping Send",
            windowMs = 30_000L,
            steps = listOf(
                "If the \"Undo\" bar is still showing at the bottom of Gmail, tap it now.",
                "Missed it? It's delivered. Send a short correction — UNDO can draft one.",
                "Tip: on gmail.com → Settings → \"Undo Send\" you can raise the window to 30 seconds.",
            ),
            caveat = "Once the Undo bar disappears, the email can't be recalled.",
        ),
        "com.microsoft.office.outlook" to MessagingGuide(
            name = "Outlook",
            canUnsend = true,
            unsendLabel = "Undo",
            window = "A few seconds after sending",
            windowMs = 10_000L,
            steps = listOf(
                "If \"Undo\" is still visible after sending, tap it.",
                "Otherwise send a short correction — UNDO can draft one.",
            ),
            caveat = "Message recall only works inside some work organisations and isn't guaranteed.",
        ),
        "com.Slack" to MessagingGuide(
            name = "Slack",
            canUnsend = true,
            unsendLabel = "Delete message",
            window = "Depends on your workspace settings",
            windowMs = null,
            steps = listOf("Long-press the message.", "Tap \"Delete message\" (or Edit to fix it)."),
            caveat = "Some workspaces don't allow members to delete messages; notifications may already have gone out.",
        ),
        "com.discord" to MessagingGuide(
            name = "Discord",
            canUnsend = true,
            unsendLabel = "Delete Message",
            window = "Any time",
            windowMs = null,
            steps = listOf("Long-press the message.", "Tap \"Delete Message\"."),
            caveat = "People may already have read it or got a push notification.",
        ),
        "com.snapchat.android" to MessagingGuide(
            name = "Snapchat",
            canUnsend = true,
            unsendLabel = "Delete",
            window = "Any time",
            windowMs = null,
            steps = listOf("Press and hold the chat or Snap you sent.", "Tap Delete and confirm."),
            caveat = "Snapchat tells the other person a chat was deleted. Opened Snaps can't be taken back.",
        ),
        "com.linkedin.android" to MessagingGuide(
            name = "LinkedIn",
            canUnsend = true,
            unsendLabel = "Delete",
            window = "Within about 60 minutes of sending",
            windowMs = 60 * MIN,
            steps = listOf("Long-press the message.", "Tap Delete (or Edit) while the option is still offered."),
            caveat = "After the window closes it can't be removed.",
        ),
        "com.microsoft.teams" to MessagingGuide(
            name = "Microsoft Teams",
            canUnsend = true,
            unsendLabel = "Delete",
            window = "Usually any time (your organisation may restrict it)",
            windowMs = null,
            steps = listOf("Long-press the message.", "Tap Delete."),
            caveat = "Organisation policies can prevent deleting messages.",
        ),
    )

    val paymentApps: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "in.amazon.mShop.android.shopping" to "Amazon",
        "com.mobikwik_new" to "MobiKwik",
        "com.sbi.lotusintouch" to "YONO SBI",
        "com.sbi.SBIFreedomPlus" to "SBI",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.csam.icici.bank.imobile" to "iMobile (ICICI)",
        "com.axis.mobile" to "Axis Mobile",
        "com.msf.kbank.mobile" to "Kotak",
        "com.freecharge.android" to "Freecharge",
        "money.super.payments" to "super.money",
        "com.paypal.android.p2pmobile" to "PayPal",
        "com.google.android.apps.walletnfcrel" to "Google Wallet",
        "com.squareup.cash" to "Cash App",
        "com.venmo" to "Venmo",
        "com.revolut.revolut" to "Revolut",
        "com.transferwise.android" to "Wise",
    )

    val smsApps = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.oneplus.mms", "com.coloros.mms", "com.miui.smsextra", "com.android.messaging",
    )

    val emailApps = setOf("com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "com.samsung.android.email.provider")
    val calendarApps = setOf("com.google.android.calendar", "com.samsung.android.calendar", "com.google.android.deskclock")

    val galleries: Map<String, GalleryGuide> = linkedMapOf(
        "com.google.android.apps.photos" to GalleryGuide("Google Photos", "Collections (or Library) → Bin / Trash", "https://photos.google.com/trash"),
        "com.sec.android.gallery3d" to GalleryGuide("Samsung Gallery", "☰ Menu → Recycle bin"),
        "com.miui.gallery" to GalleryGuide("Xiaomi Gallery", "Albums → Recently deleted / Trash bin"),
        "com.coloros.gallery3d" to GalleryGuide("Photos (OPPO / realme)", "Albums → Recently deleted"),
        "com.oneplus.gallery" to GalleryGuide("OnePlus Gallery", "Collections → Recently deleted"),
        "com.vivo.gallery" to GalleryGuide("vivo Albums", "Albums → Recently deleted"),
        "com.google.android.apps.nbu.files" to GalleryGuide("Files by Google", "☰ Menu → Trash"),
    )

    fun appName(pkg: String?): String? = pkg?.let { messagingApps[it]?.name ?: paymentApps[it] ?: galleries[it]?.name }
}
