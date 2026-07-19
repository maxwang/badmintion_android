package com.badmintonledger.domain.report

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Membership
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun fixture() =
    LedgerData(
        members =
            listOf(
                Member("A", "阿安", false),
                Member("B", "小波", false),
                Member("G", "客串", true),
                Member("H", "路人", true),
            ),
        refills =
            listOf(
                Refill(
                    "r1",
                    "2026-07-01",
                    Cents(200000),
                    Cents(250000),
                    listOf(
                        Contribution("A", Cents(100000)),
                        Contribution("B", Cents(100000)),
                    ),
                ),
            ),
        sessions =
            listOf(
                Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "B", "G")),
            ),
    )

class ReportTest {
    @Test
    fun `weekly poster payload`() {
        val w = buildWeeklyPayload(fixture(), "s1")
        assertEquals("2026-07-04", w.date)
        assertEquals("96.00", w.faceDollars)
        assertEquals("76.80", w.realDollars)
        assertEquals(3, w.players.size)
        // A: before = 1000 contributed, share 25.60, after 974.40
        assertEquals(
            PlayerRow("阿安", "1000.00", false, "25.60", "974.40", false),
            w.players[0],
        )
        // G: no contribution, before 0, owes 25.60 after
        assertEquals(
            PlayerRow("客串", "0.00", false, "25.60", "25.60", true),
            w.players[2],
        )
        // players already have their after-balance in the breakdown; no non-player has a
        // non-zero balance -> empty
        assertEquals(emptyList(), w.balances)
        assertEquals("2404.00", w.poolDollars)
    }

    @Test
    fun `weekly balance section lists only non-players with non-zero balance`() {
        var data = fixture()
        data =
            data.copy(
                members =
                    data.members +
                        // zero balance, did not play -> hidden
                        Member("C", "零哥", false) +
                        // contributed but did not play -> shown
                        Member("D", "丁叔", false),
                refills =
                    listOf(
                        data.refills[0].copy(
                            contributions = data.refills[0].contributions + Contribution("D", Cents(50000)),
                        ),
                    ),
            )
        val w = buildWeeklyPayload(data, "s1")
        assertEquals(listOf(BalanceRow("丁叔", false, "500.00")), w.balances)

        // debtors show in weeks they did not play: new week with only A and B playing
        data =
            data.copy(
                sessions = data.sessions + Session("s2", "2026-07-11", 1.0, Cents(2400), 1.0, listOf("A", "B")),
            )
        val w2 = buildWeeklyPayload(data, "s2")
        assertTrue(w2.balances.any { it.name == "客串" && it.owes })
        assertTrue(w2.balances.all { it.name != "阿安" && it.name != "小波" })
    }

    @Test
    fun `breakdown rows exclude the current week and last player absorbs remainder`() {
        // 1 hour x 25.61 x 1 = 25.61 -> three players 8.53 / 8.53 / 8.55
        val s = Session("s2", "2026-07-11", 1.0, Cents(2561), 1.0, listOf("A", "B", "G"))
        val data = fixture().copy(sessions = fixture().sessions + s)
        val rows = sessionBreakdownRows(data, s)
        // A's before-balance includes s1 (-25.60) but not s2: 1000 - 25.60 = 974.40
        assertEquals(PlayerRow("阿安", "974.40", false, "8.53", "965.87", false), rows[0])
        assertEquals("8.53", rows[1].shareDollars)
        // G already owes 25.60 from s1; last-position share 8.55; owes 34.15 after
        assertEquals(PlayerRow("客串", "25.60", true, "8.55", "34.15", true), rows[2])
    }

    @Test
    fun `monthly payload - absent guests hidden, playing members and debtors shown`() {
        val mo = buildMonthlyPayload(fixture(), "2026-07")
        assertEquals(1, mo.weeks)
        assertEquals("76.80", mo.totalDollars)
        val names = mo.rows.map { it.name }
        assertTrue("阿安" in names)
        assertTrue("客串" in names)
        assertTrue("路人" !in names)
        val g = mo.rows.first { it.name == "客串" }
        assertEquals(MonthlyRow("客串", 1, "25.60", true, "25.60"), g)
        assertEquals("2404.00", mo.poolDollars)
    }

    @Test
    fun `monthly payload - non-players without debt hidden, debtors shown even absent`() {
        var data = fixture()
        data = data.copy(members = data.members + Member("C", "零哥", false)) // zero balance
        // August has one session: only players or debtors appear (A plays, G owes); B/C/H hidden
        data =
            data.copy(
                sessions = data.sessions + Session("s8", "2026-08-01", 1.0, Cents(2400), 1.0, listOf("A")),
            )
        val names = buildMonthlyPayload(data, "2026-08").rows.map { it.name }
        assertTrue("阿安" in names) // played
        assertTrue("小波" !in names) // positive balance but no debt -> hidden
        assertTrue("客串" in names) // did not play but owes
        assertTrue("零哥" !in names) // zero balance
        assertTrue("路人" !in names) // zero balance
    }

    @Test
    fun `monthly payload - empty month returns zeros`() {
        val mo = buildMonthlyPayload(fixture(), "2026-06")
        assertEquals(0, mo.weeks)
        assertEquals("0.00", mo.totalDollars)
    }

    @Test
    fun `unpaid membership fee stays out of court balances, appears only in membershipDebts`() {
        var data = fixture()
        data =
            data.copy(
                members = data.members + Member("M", "欠年费者", false),
                memberships = listOf(Membership("mf1", "M", 2026, "2026-07-01", Cents(5000))),
            )
        val w = buildWeeklyPayload(data, "s1")
        assertTrue(w.balances.none { it.name == "欠年费者" })
        assertEquals(listOf(MembershipDebtRow("欠年费者", "50.00")), w.membershipDebts)

        val mo = buildMonthlyPayload(data, "2026-07")
        assertTrue(mo.rows.none { it.name == "欠年费者" })
        assertEquals(listOf(MembershipDebtRow("欠年费者", "50.00")), mo.membershipDebts)
    }

    @Test
    fun `membershipDebtRows excludes paid entries`() {
        val data =
            fixture().copy(
                members = fixture().members + Member("M", "已付年费者", false),
                memberships = listOf(Membership("mf1", "M", 2026, "2026-07-01", Cents(5000), paidDate = "2026-07-10")),
            )
        assertEquals(emptyList(), membershipDebtRows(data))
    }
}
