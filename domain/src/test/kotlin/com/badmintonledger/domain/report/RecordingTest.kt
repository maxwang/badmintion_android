package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingTest {
    // A funds 600 and plays (574.40 left); C funds 800, never plays; D played once and
    // paid the exact share in cash (balance 0); G is a guest who owes 25.60.
    private fun fixture() =
        LedgerData(
            members =
                listOf(
                    Member("A", "阿安", false),
                    Member("C", "陈叔", false),
                    Member("D", "大东", false),
                    Member("G", "客串", true),
                ),
            refills =
                listOf(
                    Refill(
                        "r1",
                        "2026-07-01",
                        Cents(140000),
                        Cents(175000),
                        listOf(
                            Contribution("A", Cents(60000)),
                            Contribution("C", Cents(80000)),
                        ),
                    ),
                ),
            payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
            sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "D", "G"))),
        )

    @Test
    fun `session preview - 4h at 24 dollars and factor 0_8 for 3 players`() {
        assertEquals(
            SessionPreview(faceDollars = "96.00", realDollars = "76.80", players = 3, perPersonDollars = "25.60"),
            buildSessionPreview(hours = 4.0, rateCents = 2400, factor = 0.8, playerCount = 3),
        )
    }

    @Test
    fun `session preview - per-person amount floors like the WeChat preview`() {
        // 1h x $1.25 x 0.8 = 100 cents; 100 / 3 floors to 33
        assertEquals(
            SessionPreview("1.25", "1.00", 3, "0.33"),
            buildSessionPreview(1.0, 125, 0.8, 3),
        )
    }

    @Test
    fun `session preview - null while any input is invalid`() {
        assertNull(buildSessionPreview(null, 2400, 0.8, 3))
        assertNull(buildSessionPreview(0.0, 2400, 0.8, 3))
        assertNull(buildSessionPreview(4.0, null, 0.8, 3))
        assertNull(buildSessionPreview(4.0, 2400, 0.0, 3))
        assertNull(buildSessionPreview(4.0, 2400, 0.8, 0))
    }

    @Test
    fun `refill factor text - 4 decimals or em dash`() {
        assertEquals("0.8000", refillFactorText(200000, 250000))
        assertEquals("0.7500", refillFactorText(180000, 240000))
        assertEquals("1.2500", refillFactorText(250000, 200000))
        assertEquals("—", refillFactorText(null, 250000))
        assertEquals("—", refillFactorText(200000, 0))
    }

    @Test
    fun `payment summary - debtors and all-member reference rows`() {
        val s = buildPaymentSummary(fixture())
        assertEquals(listOf(DebtorRow("G", "客串", 2560, "25.60")), s.debtors)
        assertEquals(
            listOf(
                PaymentMemberRow("A", "阿安", isGuest = false, owes = false, absDollars = "574.40"),
                PaymentMemberRow("C", "陈叔", isGuest = false, owes = false, absDollars = "800.00"),
                PaymentMemberRow("D", "大东", isGuest = false, owes = false, absDollars = "0.00"),
                PaymentMemberRow("G", "客串", isGuest = true, owes = true, absDollars = "25.60"),
            ),
            s.rows,
        )
    }

    @Test
    fun `payment summary - empty ledger`() {
        val s = buildPaymentSummary(LedgerData())
        assertEquals(emptyList(), s.debtors)
        assertEquals(emptyList(), s.rows)
    }
}
