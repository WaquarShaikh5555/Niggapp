package app.undo.engine

enum class NotifKind {
    OTP, PROMO, PAYMENT_DEBIT, PAYMENT_OTHER, SUBSCRIPTION, MESSAGE, CALL, EMAIL, CALENDAR,
    DELIVERY, SECURITY, NOISE, OTHER,
}

data class NotificationInput(
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String? = null,
    /** android.app.Notification.category, e.g. "msg", "email", "call". */
    val category: String? = null,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
    val hasProgress: Boolean = false,
    val isMedia: Boolean = false,
    val isConversation: Boolean = false,
)

data class Classification(
    val kind: NotifKind,
    /** 0..100 — how much a person would care to get this back if they lost it. */
    val importance: Int,
    val payment: ParsedPayment? = null,
    val renewalInMs: Long? = null,
) {
    /** Never persist one-time codes, promotions or system chatter. */
    val storable: Boolean get() = kind != NotifKind.OTP && kind != NotifKind.PROMO && kind != NotifKind.NOISE && importance >= 25
}

/**
 * Transparent, on-device rules. No model, no network — just weighted signals from the notification's
 * own category, the posting app, and its wording. Easy to audit, easy to explain.
 */
object Classifier {
    private val otp = Regex("(?i)(\\botp\\b|one[- ]time (?:password|passcode|code|pin)|verification code|security code|login code|sign[- ]in code|auth(?:entication)? code|\\b2fa\\b|is your (?:[a-z]+ )?code|code is \\d{4,8}|\\b\\d{4,8}\\b is your)")
    private val promo = Regex("(?i)(\\b\\d{1,2}% off\\b|\\bsale\\b|\\boffer\\b|\\bdeals?\\b|\\bcoupon\\b|\\bdiscount\\b|cashback up to|\\bflat ₹|\\bshop now\\b|\\blimited time\\b|\\bhurry\\b|\\bdon't miss\\b|\\bexclusive\\b|\\bfree trial\\b|\\bnew arrivals?\\b|\\bwin\\b|\\bvoucher\\b)")
    private val subscription = Regex("(?i)(\\bsubscription\\b|\\bmembership\\b|\\btrial (?:ends|ending|expires|will end)\\b|\\bauto-?pay\\b|\\bautopay\\b|\\bmandate\\b|\\brenew(?:al|s|ed|ing)?\\b|will be (?:auto-?)?(?:charged|debited|deducted)|\\bbilling\\b|\\bplan (?:expires|renews)\\b)")
    private val security = Regex("(?i)(new (?:sign-?in|login|device)|suspicious|password (?:was )?changed|unusual activity|security alert|was used to sign in|login attempt|account (?:locked|access))")
    private val delivery = Regex("(?i)(out for delivery|\\bdelivered\\b|arriving (?:today|tomorrow)|\\bshipped\\b|order (?:#|no|confirmed|placed)|your (?:cab|ride|driver) (?:is|has)|boarding|gate change|flight)")
    private val missedCall = Regex("(?i)(missed (?:voice |video )?call|missed calls?)")
    private val renewIn = Regex("(?i)\\b(?:in|within) (\\d{1,2}) days?\\b")
    private val renewTomorrow = Regex("(?i)\\btomorrow\\b")
    private val renewToday = Regex("(?i)\\btoday\\b|\\btonight\\b")

    private const val DAY = 24L * 60 * 60 * 1000

    fun classify(n: NotificationInput): Classification {
        if (n.ongoing || n.hasProgress || n.isMedia) return Classification(NotifKind.NOISE, 0)
        val full = listOfNotNull(n.title, n.bigText ?: n.text).joinToString(" \u2022 ")
        if (full.isBlank()) return Classification(NotifKind.NOISE, 0)

        if (otp.containsMatchIn(full)) return Classification(NotifKind.OTP, 0)

        val pkg = n.packageName
        val payment = PaymentParser.parse(full)
        val paymentCapable = pkg in AppCatalog.paymentApps || pkg in AppCatalog.smsApps || payment?.reference != null ||
            Regex("(?i)(a/c|\\bvpa\\b|upi|\\bcard\\b)").containsMatchIn(full)

        if (payment != null && paymentCapable) {
            when (payment.direction) {
                PaymentDirection.DEBIT -> return Classification(NotifKind.PAYMENT_DEBIT, 85, payment)
                PaymentDirection.UPCOMING ->
                    if (subscription.containsMatchIn(full)) {
                        return Classification(NotifKind.SUBSCRIPTION, 65, payment, renewalWindow(full))
                    }
                else -> return Classification(NotifKind.PAYMENT_OTHER, 45, payment)
            }
        }

        if (subscription.containsMatchIn(full) && !promo.containsMatchIn(full)) {
            return Classification(NotifKind.SUBSCRIPTION, 60, payment, renewalWindow(full))
        }
        if (security.containsMatchIn(full)) return Classification(NotifKind.SECURITY, 75)

        val cat = n.category
        if (cat == "call" || cat == "missed_call" || missedCall.containsMatchIn(full)) return Classification(NotifKind.CALL, 70)
        if (cat == "msg" || n.isConversation || pkg in AppCatalog.messagingApps.keys) return Classification(NotifKind.MESSAGE, 60)
        if (cat == "email" || pkg in AppCatalog.emailApps) return Classification(NotifKind.EMAIL, 45)
        if (cat == "event" || cat == "reminder" || cat == "alarm" || pkg in AppCatalog.calendarApps) return Classification(NotifKind.CALENDAR, 55)
        if (promo.containsMatchIn(full) || cat == "promo" || cat == "recommendation" || cat == "social") return Classification(NotifKind.PROMO, 5)
        if (cat == "transport" || delivery.containsMatchIn(full)) return Classification(NotifKind.DELIVERY, 45)
        if (cat == "sys" || cat == "service" || cat == "progress" || cat == "status" || cat == "navigation" || cat == "transport_media") {
            return Classification(NotifKind.NOISE, 0)
        }
        if (n.groupSummary) return Classification(NotifKind.OTHER, 20)
        return Classification(NotifKind.OTHER, 30)
    }

    private fun renewalWindow(text: String): Long? = when {
        renewIn.containsMatchIn(text) -> renewIn.find(text)!!.groupValues[1].toLong() * DAY
        renewTomorrow.containsMatchIn(text) -> DAY
        renewToday.containsMatchIn(text) -> DAY / 2
        else -> null
    }
}
