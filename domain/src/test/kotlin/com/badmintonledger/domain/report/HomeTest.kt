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

class HomeTest {
    // A funds 600 and plays (574.40 left); C funds 800, never plays (shown at 800);
    // D never funds, played once and paid the exact share in cash (balance 0 -> hidden);
    // G is a guest who played without funding (owes 25.60 -> shown).
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
            sessions =
                listOf(
                    Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "D", "G")),
                ),
        )

    @Test
    fun `rows hide zero-balance members who never funded, keep funded and owing ones`() {
        val s = buildHomeSummary(fixture(), "2026-07-15")
        assertEquals(listOf("A", "C", "G"), s.rows.map { it.id })
        assertEquals(HomeRow("A", "阿安", false, owes = false, absDollars = "574.40"), s.rows[0])
        assertEquals(HomeRow("C", "陈叔", false, owes = false, absDollars = "800.00"), s.rows[1])
        assertEquals(HomeRow("G", "客串", true, owes = true, absDollars = "25.60"), s.rows[2])
        assertEquals(false, s.empty)
    }

    @Test
    fun `pool remaining and warning threshold - strictly below 4h at default rate`() {
        val s = buildHomeSummary(fixture(), "2026-07-15")
        // pool = 1750.00 - face 96.00 = 1654.00; threshold 4h x $24 = 96.00 -> no warning
        assertEquals("1654.00", s.poolDollars)
        assertEquals(false, s.poolWarn)

        // drain the pool to exactly the threshold: still no warning (strict <)
        val atThreshold =
            fixture().copy(
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(140000),
                            Cents(9600 + 9600),
                            listOf(
                                Contribution("A", Cents(60000)),
                                Contribution("C", Cents(80000)),
                            ),
                        ),
                    ),
            )
        assertEquals(false, buildHomeSummary(atThreshold, "2026-07-15").poolWarn)

        // one cent below the threshold: warn
        val belowThreshold =
            fixture().copy(
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(140000),
                            Cents(9600 + 9599),
                            listOf(
                                Contribution("A", Cents(60000)),
                                Contribution("C", Cents(80000)),
                            ),
                        ),
                    ),
            )
        assertEquals(true, buildHomeSummary(belowThreshold, "2026-07-15").poolWarn)
    }

    @Test
    fun `empty ledger - empty flag and default pool`() {
        val s = buildHomeSummary(LedgerData(), "2026-07-15")
        assertEquals(true, s.empty)
        assertEquals(emptyList(), s.rows)
        assertEquals("0.00", s.poolDollars)
        assertEquals(true, s.poolWarn) // 0 < 4h x $24
    }
}
