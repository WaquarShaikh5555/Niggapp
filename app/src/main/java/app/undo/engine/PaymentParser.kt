package app.undo.engine

enum class PaymentDirection { DEBIT, CREDIT, FAILED, REQUEST, UPCOMING }

data class ParsedPayment(
    val amountMinor: Long,
    val currency: String,
    val direction: PaymentDirection,
    val counterparty: String?,
    val reference: String?,
    val instrument: String?,
)

/**
 * Rule-based parser for payment notifications (UPI apps, bank apps, SMS-app notifications of bank
 * messages, wallets). Deliberately conservative: it would rather miss a payment than invent one.
 */
object PaymentParser {
    private const val NUM = "([0-9]{1,3}(?:,[0-9]{2,3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    private val prefixAmount = Regex("(?i)(₹|(?<![a-z])rs\\.?|(?<![a-z])inr|(?<![a-z])usd|us\\$|\\$|€|(?<![a-z])eur|£|(?<![a-z])gbp)\\s?$NUM")
    private val suffixAmount = Regex("(?i)$NUM\\s?(inr|rs\\.?|rupees|usd|eur|gbp)(?![a-z])")
    // Bank SMS often omit the currency: "A/C X1234 debited by 2000.0" (assumed INR, the only market where this format is common).
    private val keywordAmount = Regex("(?i)\\b(?:debited|credited|deducted)\\s+(?:by|for|with)\\s+$NUM(?!\\s?(?:usd|eur|gbp))")

    private val debit = Regex("(?i)\\b(debited|sent|paid|transferred|spent|withdrawn|deducted|charged|purchased?|payment (?:of|successful|done|completed|made)|txn of|trf to|money sent)\\b")
    private val credit = Regex("(?i)\\b(credited|received|refunded|refund of|deposited|added to (?:your )?(?:wallet|account|a/c)|cashback of)\\b")
    private val failed = Regex("(?i)\\b(failed|declined|unsuccessful|reversed|could not be|couldn't be|was not completed)\\b")
    private val request = Regex("(?i)\\b(has requested|requested|requesting|collect request|payment request|request for)\\b")
    private val upcoming = Regex("(?i)(will be (?:debited|charged|deducted|auto-?debited)|\\bis due\\b|\\bdue (?:on|by|date|today|tomorrow)\\b|\\bamount due\\b|\\bpay now\\b|\\bupcoming\\b|\\bscheduled (?:for|on)\\b|\\bbill (?:of|for)\\b|\\bminimum due\\b)")
    private val promo = Regex("(?i)\\b(offer|cashback up to|win|won|get flat|get up to|earn|rewards?|coupon|discount|% off|deals?|sale|voucher|scratch card|lucky|claim now|pre-?approved|loan of|limit of)\\b")
    private val strongMarker = Regex("(?i)(a/c|\\baccount\\b|upi ref|\\bvpa\\b|\\btxn\\b|ref ?no|\\butr\\b|paid to|sent to|debited|credited|transferred to)")
    private val balanceBefore = Regex("(?i)(bal|balance|limit|avl|available|outstanding|due)[^0-9]{0,14}$")

    private val vpa = Regex("(?i)\\b(?:to|vpa)\\s+(?:vpa\\s+)?([a-z0-9._-]{2,}@[a-z][a-z0-9.]{1,20})")
    private const val STOP = "(?=\\s+(?:on|via|using|from|through|ref|refno|upi|successfully|is|was|for|at|with|and)\\b|\\s*[.,!;:\\n(•|–—]|\\s+-\\s|$)"
    private val paidTo = Regex("(?i)\\b(?:paid|sent|transferred|trf|payment|money sent)\\s+to\\s+([A-Za-z0-9][A-Za-z0-9 .&'_-]{1,40}?)$STOP")
    private val genericTo = Regex("\\b[Tt]o\\s+([A-Z][A-Za-z0-9 .&'_-]{1,40}?)$STOP")
    private val atMerchant = Regex("\\b(?:at|At)\\s+([A-Z0-9][A-Za-z0-9 .&'*_-]{1,40}?)(?=\\s+(?:on|via|using|for|ref)\\b|\\s*[.,!;:\\n(•|–—]|\\s+-\\s|$)")
    private val reference = Regex("(?i)\\b(?:upi\\s*ref(?:erence)?|ref(?:erence)?|txn|transaction|utr|rrn)\\s*(?:no\\.?|number|id|#)?\\s*[:.#-]?\\s*([a-z0-9]{6,24})\\b")

    private val badCounterparty = Regex("(?i)^(your|you|a/c|ac|account|the|bank|xx|card|wallet|beneficiary)\\b")

    fun parse(raw: String?): ParsedPayment? {
        if (raw.isNullOrBlank()) return null
        val text = raw.replace('\n', ' ')
        data class Amt(val start: Int, val minor: Long, val currency: String)

        val amounts = mutableListOf<Amt>()
        prefixAmount.findAll(text).forEach { m ->
            toMinor(m.groupValues[2])?.let { amounts += Amt(m.range.first, it, currencyOf(m.groupValues[1])) }
        }
        suffixAmount.findAll(text).forEach { m ->
            toMinor(m.groupValues[1])?.let { amounts += Amt(m.range.first, it, currencyOf(m.groupValues[2])) }
        }
        if (amounts.isEmpty()) {
            keywordAmount.findAll(text).forEach { m ->
                toMinor(m.groupValues[1])?.let { amounts += Amt(m.groups[1]!!.range.first, it, "INR") }
            }
        }
        val usable = amounts.filter { a ->
            a.minor > 0 && !balanceBefore.containsMatchIn(text.substring(0, a.start))
        }
        if (usable.isEmpty()) return null

        val direction: PaymentDirection = when {
            request.containsMatchIn(text) -> PaymentDirection.REQUEST
            failed.containsMatchIn(text) -> PaymentDirection.FAILED
            upcoming.containsMatchIn(text) -> PaymentDirection.UPCOMING
            else -> {
                val d = debit.find(text)?.range?.first ?: -1
                val c = credit.find(text)?.range?.first ?: -1
                when {
                    d < 0 && c < 0 -> return null
                    d >= 0 && (c < 0 || d < c) -> PaymentDirection.DEBIT
                    else -> PaymentDirection.CREDIT
                }
            }
        }

        // Marketing guard: promos mention money too ("Get ₹100 cashback").
        if (promo.containsMatchIn(text) && !strongMarker.containsMatchIn(text)) return null

        val anchor = (debit.find(text) ?: credit.find(text))?.range?.first ?: 0
        val chosen = usable.minByOrNull { kotlin.math.abs(it.start - anchor) } ?: return null

        val counterparty = findCounterparty(text, direction)
        val ref = reference.findAll(text).map { it.groupValues[1] }
            .firstOrNull { r -> r.count { it.isDigit() } >= 4 }

        val lower = text.lowercase()
        val instrument = when {
            "upi" in lower || "vpa" in lower -> "UPI"
            "card" in lower -> "Card"
            "wallet" in lower -> "Wallet"
            "a/c" in lower || "account" in lower || "neft" in lower || "imps" in lower || "rtgs" in lower -> "Bank transfer"
            else -> null
        }
        return ParsedPayment(chosen.minor, chosen.currency, direction, counterparty, ref, instrument)
    }

    private fun findCounterparty(text: String, direction: PaymentDirection): String? {
        val candidates = sequence {
            vpa.find(text)?.let { yield(it.groupValues[1]) }
            paidTo.find(text)?.let { yield(it.groupValues[1]) }
            if (direction == PaymentDirection.DEBIT) {
                genericTo.find(text)?.let { yield(it.groupValues[1]) }
                atMerchant.find(text)?.let { yield(it.groupValues[1]) }
            }
        }
        return candidates
            .map { it.trim().trimEnd('.', '-', '_') }
            .firstOrNull { it.length >= 2 && !badCounterparty.containsMatchIn(it) && it.any { ch -> ch.isLetter() } }
    }

    private fun toMinor(num: String): Long? {
        val clean = num.replace(",", "")
        val parts = clean.split('.')
        val major = parts[0].toLongOrNull() ?: return null
        if (major > 100_000_000L) return null // implausible; probably an account/phone number
        val cents = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toIntOrNull() ?: 0 else 0
        return major * 100 + cents
    }

    private fun currencyOf(token: String): String {
        val t = token.lowercase().trimEnd('.')
        return when {
            t == "₹" || t == "rs" || t == "inr" || t == "rupees" -> "INR"
            t == "$" || t == "usd" || t == "us$" -> "USD"
            t == "€" || t == "eur" -> "EUR"
            t == "£" || t == "gbp" -> "GBP"
            else -> "INR"
        }
    }
}
