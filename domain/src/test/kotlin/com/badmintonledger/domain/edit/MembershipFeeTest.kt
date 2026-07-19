package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Membership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MembershipFeeTest {
    @Test
    fun `add validates then rejects a duplicate member-year pair`() {
        val data = addMember(LedgerData(), "A", "阿安", false).data
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "A", 2026, "2026-07-01", amountCents = -100))
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "A", 2026, "bad-date", amountCents = 5000))
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "A", 0, "2026-07-01", amountCents = 5000))
        assertIs<EditResult.Err>(addMembershipFee(data, "mf1", "", 2026, "2026-07-01", amountCents = 5000))

        val added = addMembershipFee(data, "mf1", "A", 2026, "2026-07-01", amountCents = 5000)
        assertIs<EditResult.Ok<Membership>>(added)
        assertEquals(Membership("mf1", "A", 2026, "2026-07-01", Cents(5000)), added.value)

        val dup = addMembershipFee(added.data, "mf2", "A", 2026, "2026-07-15", amountCents = 5000)
        assertEquals(EditResult.Err("该成员该年度已收取会费"), dup)

        val nextYear = addMembershipFee(added.data, "mf3", "A", 2027, "2027-07-01", amountCents = 5000)
        assertIs<EditResult.Ok<Membership>>(nextYear)
        assertEquals(2, nextYear.data.memberships.size)
    }

    @Test
    fun `delete removes only the matching entry`() {
        val data = LedgerData(memberships = listOf(Membership("mf1", "A", 2026, "2026-07-01", Cents(5000))))
        assertEquals(emptyList(), deleteMembershipFee(data, "mf1").memberships)
    }

    @Test
    fun `even split across two eligible members, skipping guest and disabled`() {
        var data = addMember(LedgerData(), "A", "阿安", false).data
        data = addMember(data, "B", "小波", false).data
        data = addMember(data, "G", "客串", true).data
        data = addMember(data, "C", "陈叔", false).data
        data = setActive(data, "C", false)
        data = (addMembershipFee(data, "mf0", "B", 2026, "2026-06-01", amountCents = 5000) as EditResult.Ok).data

        val r =
            chargeAnnualMembershipFee(data, listOf("mf1", "mf2"), 2026, totalAmountDollars = 50.0, date = "2026-07-01")
        assertIs<EditResult.Ok<MembershipChargeResult>>(r)
        assertEquals(listOf("阿安"), r.value.chargedNames)
        assertEquals(listOf("小波"), r.value.skippedNames)
        assertEquals(2, r.data.memberships.size)
        assertEquals(2500L, r.data.memberships.first { it.memberId == "A" }.amount.value) // $50 / 2 = $25
        assertEquals(5000L, r.data.memberships.first { it.memberId == "B" }.amount.value) // untouched by this run

        // repeat run: everyone already billed -> all skipped, no new entries
        val r2 = chargeAnnualMembershipFee(r.data, listOf("mfx1", "mfx2"), 2026, 50.0, "2026-07-02")
        assertIs<EditResult.Ok<MembershipChargeResult>>(r2)
        assertEquals(emptyList(), r2.value.chargedNames)
        assertEquals(2, r2.data.memberships.size)
    }

    @Test
    fun `remainder absorbed by the last candidate, sum exact`() {
        var data = addMember(LedgerData(), "A", "阿安", false).data
        data = addMember(data, "B", "小波", false).data
        data = addMember(data, "C", "陈叔", false).data
        // $50 / 3: base = 5000 / 3 = 1666 cents; last gets 5000 - 1666*2 = 1668
        val r = chargeAnnualMembershipFee(data, listOf("mf1", "mf2", "mf3"), 2026, 50.0, "2026-07-01")
        assertIs<EditResult.Ok<MembershipChargeResult>>(r)
        assertEquals(listOf("阿安", "小波", "陈叔"), r.value.chargedNames)
        val amounts = listOf("A", "B", "C").map { id -> r.data.memberships.first { it.memberId == id }.amount.value }
        assertEquals(listOf(1666L, 1666L, 1668L), amounts)
        assertEquals(5000L, amounts.sum())
    }

    @Test
    fun `validates amount and date before charging anyone`() {
        val data = addMember(LedgerData(), "A", "阿安", false).data
        assertIs<EditResult.Err>(chargeAnnualMembershipFee(data, listOf("mf1"), 2026, -1.0, "2026-07-01"))
        assertIs<EditResult.Err>(chargeAnnualMembershipFee(data, listOf("mf1"), 2026, 50.0, "bad-date"))
        assertEquals(emptyList(), data.memberships)
    }

    @Test
    fun `empty candidate pool returns empty results without dividing by zero`() {
        val data = addMember(LedgerData(), "G", "客串", true).data // guest only, no eligible members
        val r = chargeAnnualMembershipFee(data, emptyList(), 2026, 50.0, "2026-07-01")
        assertEquals(EditResult.Ok(data, MembershipChargeResult(emptyList(), emptyList())), r)
    }

    @Test
    fun `setMembershipFeePaid toggles paidDate, rejects bad date and unknown id`() {
        val data = (addMembershipFee(LedgerData(), "mf1", "A", 2026, "2026-07-01", 5000) as EditResult.Ok).data
        assertEquals(null, data.memberships[0].paidDate)

        assertIs<EditResult.Err>(setMembershipFeePaid(data, "mf1", true, "bad-date"))

        val paid = setMembershipFeePaid(data, "mf1", true, "2026-07-15")
        assertIs<EditResult.Ok<Unit>>(paid)
        assertEquals("2026-07-15", paid.data.memberships[0].paidDate)

        val unpaid = setMembershipFeePaid(paid.data, "mf1", false, null)
        assertIs<EditResult.Ok<Unit>>(unpaid)
        assertEquals(null, unpaid.data.memberships[0].paidDate)

        assertEquals(EditResult.Err("记录不存在"), setMembershipFeePaid(data, "nope", true, "2026-07-15"))
    }
}
