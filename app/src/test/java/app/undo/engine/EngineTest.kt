package app.undo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {

    @Test fun bankSmsUpiDebit() {
        val p = PaymentParser.parse("Rs.2,500.00 debited from A/c XX1234 on 02-10-26 to VPA rahul.k@okaxis UPI Ref 627512345678. Not you? Call 18001234")!!
        assertEquals(250_000L, p.amountMinor)
        assertEquals("INR", p.currency)
        assertEquals(PaymentDirection.DEBIT, p.direction)
        assertEquals("rahul.k@okaxis", p.counterparty)
        assertEquals("627512345678", p.reference)
        assertEquals("UPI", p.instrument)
    }

    @Test fun upiAppPaidTo() {
        val p = PaymentParser.parse("₹500 paid to Priya Sharma • Paid via HDFC Bank. UPI transaction ID: 412345678901")!!
        assertEquals(50_000L, p.amountMinor)
        assertEquals(PaymentDirection.DEBIT, p.direction)
        assertEquals("Priya Sharma", p.counterparty)
        assertEquals("412345678901", p.reference)
    }

    @Test fun ignoresBalanceAmount() {
        val p = PaymentParser.parse("INR 1,200.00 spent on your card XX9876 at AMAZON on 02-Oct. Avl Lmt: INR 45,000.00")!!
        assertEquals(120_000L, p.amountMinor)
        assertEquals("AMAZON", p.counterparty)
        assertEquals("Card", p.instrument)
    }

    @Test fun creditIsNotDebit() {
        val p = PaymentParser.parse("Your A/c XX1234 is credited with Rs 750.00 by VPA amit@ybl. UPI Ref 999912345678")!!
        assertEquals(PaymentDirection.CREDIT, p.direction)
    }

    @Test fun requestIsNotAPayment() {
        assertEquals(PaymentDirection.REQUEST, PaymentParser.parse("Rohit has requested ₹300 from you on Google Pay")!!.direction)
    }

    @Test fun failedPayment() {
        assertEquals(PaymentDirection.FAILED, PaymentParser.parse("Payment of ₹1,000 to Zomato failed. Money will be refunded if debited.")!!.direction)
    }

    @Test fun upcomingAutopay() {
        assertEquals(PaymentDirection.UPCOMING, PaymentParser.parse("₹199 will be debited on 05 Oct for your Netflix subscription via UPI AutoPay")!!.direction)
    }

    @Test fun promoIsIgnored() {
        assertNull(PaymentParser.parse("Get flat ₹100 cashback on your next 3 payments! Offer ends tonight"))
    }

    @Test fun noAmountNoPayment() {
        assertNull(PaymentParser.parse("Your order has been shipped and will arrive tomorrow"))
    }

    @Test fun dollars() {
        val p = PaymentParser.parse("You sent $45.20 to Jamie Lee")!!
        assertEquals(4520L, p.amountMinor)
        assertEquals("USD", p.currency)
        assertEquals("Jamie Lee", p.counterparty)
    }

    @Test fun wordsEndingInRsAreNotRupees() {
        // "hours 5" must not be read as "rs 5"
        assertNull(PaymentParser.parse("Your delivery partner will arrive in 2 hours 5 minutes"))
    }
}

class ClassifierTest {
    private fun n(pkg: String, title: String?, text: String?, category: String? = null) =
        NotificationInput(packageName = pkg, title = title, text = text, category = category)

    @Test fun otpIsNeverStored() {
        val c = Classifier.classify(n("com.google.android.apps.messaging", "AX-HDFCBK", "123456 is your OTP for txn of Rs 2,000 at AMAZON. Do not share."))
        assertEquals(NotifKind.OTP, c.kind)
        assertFalse(c.storable)
    }

    @Test fun bankSmsDebitIsPayment() {
        val c = Classifier.classify(n("com.google.android.apps.messaging", "VM-SBIUPI", "Dear UPI user A/C X1234 debited by 2000.0 on date 02Oct26 trf to RAHUL KUMAR Refno 627512345678"))
        assertEquals(NotifKind.PAYMENT_DEBIT, c.kind)
        assertNotNull(c.payment)
    }

    @Test fun gpayPayment() {
        val c = Classifier.classify(n("com.google.android.apps.nbu.paisa.user", "₹500 paid to Priya Sharma", "Paid via HDFC Bank"))
        assertEquals(NotifKind.PAYMENT_DEBIT, c.kind)
        assertEquals(50_000L, c.payment!!.amountMinor)
    }

    @Test fun subscriptionRenewal() {
        val c = Classifier.classify(n("com.android.vending", "Your subscription renews tomorrow", "Spotify Premium will renew for ₹119 tomorrow"))
        assertEquals(NotifKind.SUBSCRIPTION, c.kind)
        assertEquals(24L * 60 * 60 * 1000, c.renewalInMs)
    }

    @Test fun whatsappMessage() {
        val c = Classifier.classify(n("com.whatsapp", "Mom", "Call me when you land"))
        assertEquals(NotifKind.MESSAGE, c.kind)
        assertTrue(c.storable)
    }

    @Test fun promoNotStored() {
        val c = Classifier.classify(n("com.myntra.android", "Big Fashion Sale", "Flat 70% off on 10,000+ styles. Shop now!"))
        assertEquals(NotifKind.PROMO, c.kind)
        assertFalse(c.storable)
    }

    @Test fun ongoingIsNoise() {
        val c = Classifier.classify(NotificationInput("com.spotify.music", "Song", "Artist", ongoing = true))
        assertEquals(NotifKind.NOISE, c.kind)
    }

    @Test fun missedCall() {
        assertEquals(NotifKind.CALL, Classifier.classify(n("com.google.android.dialer", "Missed call", "Dad")).kind)
    }
}

class PriorityTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L

    @Test fun freshPaymentNeedsAttention() {
        val e = UndoEvent(type = EventType.PAYMENT_SENT, occurredAt = now - 2 * min, title = "Paid", amountMinor = 500_000)
        assertTrue(Priority.needsAttention(e, now))
    }

    @Test fun oldDismissalFades() {
        val e = UndoEvent(type = EventType.NOTIFICATION_DISMISSED, occurredAt = now - 6 * 60 * min, title = "x")
        assertFalse(Priority.needsAttention(e, now))
    }

    @Test fun biggerPaymentRanksHigher() {
        val small = UndoEvent(id = 1, type = EventType.PAYMENT_SENT, occurredAt = now - min, title = "a", amountMinor = 1_000)
        val big = UndoEvent(id = 2, type = EventType.PAYMENT_SENT, occurredAt = now - min, title = "b", amountMinor = 5_000_000)
        assertTrue(Priority.score(big, now) > Priority.score(small, now))
    }

    @Test fun resolvedNeverNeedsAttention() {
        val e = UndoEvent(type = EventType.PAYMENT_SENT, occurredAt = now, title = "x", status = EventStatus.NOT_A_MISTAKE)
        assertFalse(Priority.needsAttention(e, now))
    }

    @Test fun closingTrashWindowIsUrgent() {
        val closing = UndoEvent(type = EventType.FILE_TRASHED, occurredAt = now - 3 * 60 * min, title = "x", expiresAt = now + 30 * min)
        val open = closing.copy(expiresAt = now + 20L * 24 * 60 * min)
        assertTrue(Priority.score(closing, now) > Priority.score(open, now))
    }
}

class MoneyAndTextTest {
    @Test fun indianGrouping() = assertEquals("₹1,23,456", Money.format(12_345_600, "INR"))
    @Test fun cents() = assertEquals("$1,234.50", Money.format(123_450, "USD"))
    @Test fun stripsBidiAndControl() = assertEquals("abc def", TextSan.clean("a\u202Eb\u0000c   def"))
    @Test fun truncates() = assertEquals("abc…", TextSan.clean("abcdef", max = 3))
}

class PlaybookTest {
    private val ctx = PlanContext(now = 1_800_000_000_000L, isIndia = true)

    @Test fun paymentNeverClaimsReversal() {
        val e = UndoEvent(type = EventType.PAYMENT_SENT, occurredAt = ctx.now, title = "Paid", amountMinor = 100_00, currency = "INR")
        val plan = Playbooks.forEvent(e, ctx)
        assertEquals(Undoability.NOT_UNDOABLE, plan.undoability)
        assertTrue(plan.verdictTitle.contains("can't"))
    }

    @Test fun expiredTrashIsNotUndoable() {
        val e = UndoEvent(type = EventType.FILE_TRASHED, occurredAt = ctx.now - 1000, title = "x", expiresAt = ctx.now - 1)
        assertEquals(Undoability.GUIDED, Playbooks.forEvent(e, ctx).undoability)
    }

    @Test fun everyTopicHasAPath() {
        Topic.values().forEach { t ->
            val p = Playbooks.forTopic(t, ctx)
            assertTrue("$t has no next step", p.primary != null || p.steps.isNotEmpty() || p.secondary.isNotEmpty() || p.templates.isNotEmpty())
        }
    }
}
