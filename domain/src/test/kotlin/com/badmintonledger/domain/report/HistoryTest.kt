package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Membership
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {
    private fun fixture() =
        LedgerData(
            members = listOf(Member("A", "阿安", false), Member("G", "客串", true)),
            refills =
                listOf(
                    Refill("r1", "2026-07-01", Cents(200000), Cents(250000), emptyList()),
                    Refill("r0", "2024-01-01", Cents(100000), Cents(125000), emptyList()),
                ),
            payments = listOf(Payment("p1", "G", Cents(2560), "2026-07-05")),
            sessions =
                listOf(
                    Session("sOld", "2024-06-01", 4.0, Cents(2400), 0.8, listOf("A")),
                    Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "G")),
                    Session("s2", "2026-07-11", 1.5, Cents(2561), 1.0, listOf("A")),
                ),
        )

    @Test
    fun `sessions cut off and sorted, descriptions and names match WeChat shapes`() {
        val h = buildHistoryRows(fixture(), cutoff = "2025-07-15")
        assertEquals(listOf("s2", "s1"), h.sessions.map { it.id })
        assertEquals("1.5小时 × $25.61，1人", h.sessions[0].desc)
        assertEquals("阿安", h.sessions[0].names)
        assertEquals("38.42", h.sessions[0].realDollars)
        assertEquals("4小时 × $24，2人", h.sessions[1].desc)
        assertEquals("阿安、客串", h.sessions[1].names)
        assertEquals("76.80", h.sessions[1].realDollars)
    }

    @Test
    fun `refills and payments unfiltered, sorted, with raw-dollar descriptions`() {
        val h = buildHistoryRows(fixture(), cutoff = "2025-07-15")
        assertEquals(listOf("r1", "r0"), h.refills.map { it.id })
        assertEquals("实付 $2000 → 到账 $2500", h.refills[0].desc)
        assertEquals("实付 $1000 → 到账 $1250", h.refills[1].desc)
        assertEquals("客串 交来 $25.6", h.payments[0].desc)
    }

    @Test
    fun `unknown member id renders 未知`() {
        val data = fixture().copy(payments = listOf(Payment("p2", "GHOST", Cents(100), "2026-07-06")))
        assertEquals("未知 交来 $1", buildHistoryRows(data, "2020-01-01").payments[0].desc)
    }

    @Test
    fun `memberships listed newest-first, unfiltered by cutoff, paid tag reflects paidDate`() {
        val data =
            fixture().copy(
                memberships =
                    listOf(
                        Membership("mf1", "A", 2025, "2025-07-01", Cents(5000)),
                        Membership("mf2", "A", 2026, "2026-07-01", Cents(2500), paidDate = "2026-07-10"),
                    ),
            )
        val h = buildHistoryRows(data, cutoff = "2025-07-15") // even before the cutoff, mf1 still shows
        assertEquals(listOf("mf2", "mf1"), h.memberships.map { it.id })
        assertEquals("阿安 2026年度 $25.00（已付）", h.memberships[0].desc)
        assertEquals("阿安 2025年度 $50.00（未付）", h.memberships[1].desc)
    }
}
