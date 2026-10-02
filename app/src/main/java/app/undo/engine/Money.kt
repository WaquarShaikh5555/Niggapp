package app.undo.engine

/** Deterministic money formatting (no locale surprises in tests). */
object Money {
    private val symbols = mapOf("INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£")

    fun format(minor: Long, currency: String?): String {
        val cur = currency ?: "INR"
        val symbol = symbols[cur] ?: "$cur "
        val negative = minor < 0
        val abs = kotlin.math.abs(minor)
        val major = abs / 100
        val cents = (abs % 100).toInt()
        val grouped = if (cur == "INR") groupIndian(major) else groupWestern(major)
        val decimals = if (cents == 0) "" else "." + cents.toString().padStart(2, '0')
        return (if (negative) "-" else "") + symbol + grouped + decimals
    }

    private fun groupWestern(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

    private fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$restGrouped,$last3"
    }
}
