package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.Cents
import com.badmintonledger.domain.model.LedgerData
import com.badmintonledger.domain.model.Member
import com.badmintonledger.domain.model.Membership
import com.badmintonledger.domain.model.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MemberEditTest {
    @Test
    fun `add rename setGuest remove`() {
        var data = LedgerData()
        val a = addMember(data, "m1", "阿安", false)
        data = a.data
        val g = addMember(data, "m2", "客串", true)
        data = g.data
        assertEquals(2, data.members.size)
        assertEquals(true, g.value.isGuest)

        data = renameMember(data, "m1", "安哥")
        assertEquals("安哥", data.members[0].name)
        data = setGuest(data, "m2", false)
        assertEquals(false, data.members[1].isGuest)

        val removed = removeMember(data, "m2")
        assertIs<EditResult.Ok<Unit>>(removed)
        assertEquals(1, removed.data.members.size)
    }

    @Test
    fun `rename and setGuest on unknown id are no-ops`() {
        val data = addMember(LedgerData(), "m1", "阿安", false).data
        assertEquals(data, renameMember(data, "nope", "x"))
        assertEquals(data, setGuest(data, "nope", true))
    }

    @Test
    fun `memberReferenced and hard-delete are blocked by a memberships entry alone`() {
        val data =
            LedgerData(members = listOf(Member("A", "阿安", false)))
                .copy(memberships = listOf(Membership("mf1", "A", 2026, "2026-07-01", Cents(5000))))
        assertEquals(true, memberReferenced(data, "A"))
        assertIs<EditResult.Err>(removeMember(data, "A"))
    }

    @Test
    fun `memberReferenced and hard-delete are blocked by a transfer entry alone`() {
        val data =
            LedgerData(members = listOf(Member("A", "阿安", false), Member("B", "小明", false)))
                .copy(transfers = listOf(Transfer("t1", "B", "A", Cents(1000), "2026-07-01")))
        assertEquals(true, memberReferenced(data, "A"))
        assertIs<EditResult.Err>(removeMember(data, "A"))
    }

    @Test
    fun `setActive toggles the flag, defaults true, unknown id is a no-op`() {
        val data = addMember(LedgerData(), "m1", "阿安", false).data
        assertEquals(true, data.members[0].active)
        val disabled = setActive(data, "m1", false)
        assertEquals(false, disabled.members[0].active)
        assertEquals(true, setActive(disabled, "m1", true).members[0].active)
        assertEquals(disabled, setActive(disabled, "nope", true))
    }
}
