package com.badmintonledger.domain.edit

import com.badmintonledger.domain.calc.memberBalancesCents
import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.Contribution
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Payment
import com.badmintonledger.domain.model.Refill
import com.badmintonledger.domain.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettleTest {
    // A funds the pot and plays; guests G and H play without funding and owe a 25.60 share each.
    private fun fixture() =
        LedgerData(
            members =
                listOf(
                    Member("A", "阿安", false),
                    Member("G", "客串", true),
                    Member("H", "候补", true),
                ),
            refills =
                listOf(
                    Refill("r1", "2026-07-01", Cents(60000), Cents(75000), listOf(Contribution("A", Cents(60000)))),
                ),
            sessions = listOf(Session("s1", "2026-07-04", 4.0, Cents(2400), 0.8, listOf("A", "G", "H"))),
        )

    @Test
    fun `settling one debtor records their full debt and zeroes the balance`() {
        val r = settleDebtors(fixture(), listOf("G"), listOf("p_1"), "2026-07-05")
        assertIs<EditResult.Ok<List<Payment>>>(r)
        assertEquals(listOf(Payment("p_1", "G", Cents(2560), "2026-07-05")), r.value)
        val bal = memberBalancesCents(r.data)
        assertEquals(0L, bal["G"])
        assertEquals(-2560L, bal["H"]) // untouched
    }

    @Test
    fun `settling several debtors records one payment each`() {
        val r = settleDebtors(fixture(), listOf("G", "H"), listOf("p_1", "p_2"), "2026-07-05")
        assertIs<EditResult.Ok<List<Payment>>>(r)
        assertEquals(
            listOf(
                Payment("p_1", "G", Cents(2560), "2026-07-05"),
                Payment("p_2", "H", Cents(2560), "2026-07-05"),
            ),
            r.value,
        )
        val bal = memberBalancesCents(r.data)
        assertEquals(0L, bal["G"])
        assertEquals(0L, bal["H"])
        assertEquals(2, r.data.payments.size)
    }

    @Test
    fun `empty selection is refused`() {
        assertEquals(
            EditResult.Err("请选择成员"),
            settleDebtors(fixture(), emptyList(), emptyList(), "2026-07-05"),
        )
    }

    @Test
    fun `member with nothing owing is refused`() {
        assertEquals(
            EditResult.Err("该成员当前无欠款"),
            settleDebtors(fixture(), listOf("A"), listOf("p_1"), "2026-07-05"),
        )
    }
}
