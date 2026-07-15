package com.badmintonledger.domain.calc

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.RateChange
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerCalcTest {
    @Test
    fun `hasContributed - only members who funded a refill count, cash payments do not`() {
        val data =
            LedgerData(
                members = listOf(Member("A", "阿安", false), Member("D", "大东", false)),
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(60000),
                            Cents(75000),
                            listOf(Contribution("A", Cents(60000))),
                        ),
                    ),
                payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
            )
        assertEquals(true, hasContributed(data, "A"))
        assertEquals(false, hasContributed(data, "D")) // cash payment is not a contribution
        assertEquals(false, hasContributed(data, "X"))
    }

    @Test
    fun `member balances - contributions plus cash minus shares (600-600-800 scenario)`() {
        // A/B/C fund 600/600/800 (refill 2000 pays for 2500 credit), D funds nothing
        // week 1: A, B, D play (4h x 24 x 0.8 = 76.80 -> 25.60 each); D pays 25.60 cash
        val data =
            LedgerData(
                members =
                    listOf(
                        Member("A", "阿安", false),
                        Member("B", "小波", false),
                        Member("C", "陈叔", false),
                        Member("D", "大东", false),
                    ),
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(200000),
                            Cents(250000),
                            listOf(
                                Contribution("A", Cents(60000)),
                                Contribution("B", Cents(60000)),
                                Contribution("C", Cents(80000)),
                            ),
                        ),
                    ),
                payments = listOf(Payment("p1", "D", Cents(2560), "2026-07-05")),
                sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "D"))),
            )
        val bal = memberBalancesCents(data)
        assertEquals(60000L - 2560, bal["A"]) // 574.40 left
        assertEquals(60000L - 2560, bal["B"])
        assertEquals(80000L, bal["C"]) // did not play, untouched
        assertEquals(0L, bal["D"]) // owed 25.60, paid in full

        // pool remaining = 2500 credit - 96 face = 2404
        assertEquals(250000L - 9600, poolRemainingCents(data))
    }

    @Test
    fun `payer runs dry into debt - recompute after editing history is automatic`() {
        val data =
            LedgerData(
                members = listOf(Member("E", "尔文", false)),
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(200000),
                            Cents(250000),
                            listOf(Contribution("E", Cents(100))),
                        ),
                    ),
                sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("E"))),
            )
        assertEquals(100L - 7680, memberBalancesCents(data)["E"]) // negative = owes

        // derived, not stored: editing hours recomputes correctly (2h x 24 x 0.8 = 38.40)
        val edited = data.copy(sessions = listOf(data.sessions[0].copy(hours = 2.0)))
        assertEquals(100L - 3840, memberBalancesCents(edited)["E"])
    }

    @Test
    fun `current factor - default config without refills, latest refill by date otherwise`() {
        assertEquals(0.8, currentFactor(LedgerData()))
        val data =
            LedgerData(
                refills =
                    listOf(
                        Refill("r1", "2026-01-01", Cents(200000), Cents(250000), emptyList()),
                        Refill("r2", "2026-06-01", Cents(180000), Cents(240000), emptyList()),
                    ),
            )
        assertEquals(0.75, currentFactor(data))
    }

    @Test
    fun `multiple refills accumulate the pool`() {
        val data =
            LedgerData(
                refills =
                    listOf(
                        Refill("r1", "2026-01-01", Cents(200000), Cents(250000), emptyList()),
                        Refill("r2", "2026-06-01", Cents(180000), Cents(240000), emptyList()),
                    ),
            )
        assertEquals(490000L, poolRemainingCents(data))
    }

    @Test
    fun `month summary counts only that month, absent members count zero`() {
        val data =
            LedgerData(
                members =
                    listOf(
                        Member("A", "阿安", false),
                        Member("B", "小波", false),
                        Member("G", "客串", true),
                    ),
                sessions =
                    listOf(
                        // 7680 -> 2560 x3
                        Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "G")),
                        // 4800 -> 2400 x2
                        Session("s2", "2026-07-11", 2.0, Cents(3000), 0.8, listOf("A", "B")),
                        // other month
                        Session("s3", "2026-08-01", 4.0, Cents(2400), 0.8, listOf("A")),
                    ),
            )
        val m = monthSummary(data, "2026-07")
        assertEquals(2, m.weeks)
        assertEquals(12480L, m.totalCents)
        assertEquals(MemberMonth(2, 4960L), m.perMember["A"])
        assertEquals(MemberMonth(2, 4960L), m.perMember["B"])
        assertEquals(MemberMonth(1, 2560L), m.perMember["G"])

        val empty = monthSummary(data, "2026-06")
        assertEquals(0, empty.weeks)
        assertEquals(MemberMonth(0, 0L), empty.perMember["A"])
    }

    @Test
    fun `balances can exclude one session - as if that week never happened`() {
        val data =
            LedgerData(
                members = listOf(Member("A", "阿安", false), Member("B", "小波", false)),
                refills =
                    listOf(
                        Refill(
                            "r1",
                            "2026-07-01",
                            Cents(10000),
                            Cents(12500),
                            listOf(Contribution("A", Cents(10000))),
                        ),
                    ),
                sessions =
                    listOf(
                        Session("s1", "2026-07-04", 1.0, Cents(2400), 0.8, listOf("A", "B")),
                        Session("s2", "2026-07-11", 1.0, Cents(2400), 0.8, listOf("A")),
                    ),
            )
        val all = memberBalancesCents(data)
        // s1 costs 19.20 split two ways at 9.60; s2 costs 19.20 all on A
        assertEquals(10000L - 960 - 1920, all["A"])
        val excl = memberBalancesCents(data, "s2")
        assertEquals(10000L - 960, excl["A"])
        assertEquals(-960L, excl["B"])
    }

    @Test
    fun `current rate by date - exact, between, before and after all entries`() {
        val data =
            LedgerData(
                rates =
                    listOf(
                        RateChange("rt1", "2026-01-01", Cents(2400)),
                        RateChange("rt2", "2026-06-01", Cents(2600)),
                    ),
            )
        assertEquals(Cents(2400), currentRate(data, "2026-01-01"))
        assertEquals(Cents(2400), currentRate(data, "2026-03-15"))
        assertEquals(Cents(2600), currentRate(data, "2026-06-01"))
        assertEquals(Cents(2600), currentRate(data, "2026-12-31"))
        assertEquals(Cents(2400), currentRate(data, "2025-01-01"))
    }
}
