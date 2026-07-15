package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
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
        assertEquals("1.5h × $25.61, 1 player", h.sessions[0].desc)
        assertEquals("阿安", h.sessions[0].names)
        assertEquals("38.42", h.sessions[0].realDollars)
        assertEquals("4h × $24, 2 players", h.sessions[1].desc)
        assertEquals("阿安, 客串", h.sessions[1].names)
        assertEquals("76.80", h.sessions[1].realDollars)
    }

    @Test
    fun `refills and payments unfiltered, sorted, with raw-dollar descriptions`() {
        val h = buildHistoryRows(fixture(), cutoff = "2025-07-15")
        assertEquals(listOf("r1", "r0"), h.refills.map { it.id })
        assertEquals("Paid $2000 → credit $2500", h.refills[0].desc)
        assertEquals("Paid $1000 → credit $1250", h.refills[1].desc)
        assertEquals("客串 paid $25.6", h.payments[0].desc)
    }

    @Test
    fun `unknown member id renders Unknown`() {
        val data = fixture().copy(payments = listOf(Payment("p2", "GHOST", Cents(100), "2026-07-06")))
        assertEquals("Unknown paid $1", buildHistoryRows(data, "2020-01-01").payments[0].desc)
    }
}
