package com.badmintonledger.domain.edit

import com.badmintonledger.domain.model.LedgerData
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
}
